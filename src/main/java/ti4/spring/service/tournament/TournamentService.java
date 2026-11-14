package ti4.spring.service.tournament;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.game.persistence.DatabasePersistenceGate;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

@Service
@AllArgsConstructor
public class TournamentService {

    public static final String UNAVAILABLE = "Tournaments are paused while database maintenance is in progress.";

    private final TournamentRepository tournaments;
    private final TournamentEntryRepository entries;
    private final TournamentSignupPostRepository signupPosts;
    private final TournamentGameMasterRepository gameMasters;

    public static TournamentService getBean() {
        return SpringContext.getBean(TournamentService.class);
    }

    public static boolean isUnavailable() {
        return DatabasePersistenceGate.isDisabled();
    }

    public enum JoinOutcome {
        JOINED,
        WAITLISTED,
        ALREADY_JOINED,
        ALREADY_WAITLISTED,
        CLOSED,
        NOT_FOUND
    }

    public record JoinResult(JoinOutcome outcome, int activeCount, int waitlistPosition) {}

    public record LeaveResult(boolean removed, boolean wasActive, String promotedUserId, String refusal) {

        static LeaveResult refused(String refusal) {
            return new LeaveResult(false, false, null, refusal);
        }
    }

    public record BulkAddResult(List<String> added, List<String> promoted, List<String> alreadyIn, String refusal) {}

    @Transactional
    public Tournament create(
            String seriesName,
            String roundLabel,
            int targetSize,
            int groupSize,
            boolean fogMode,
            String hostGuildId,
            String createdBy) {
        Tournament tournament = new Tournament();
        tournament.setSeriesName(seriesName);
        tournament.setRoundLabel(roundLabel);
        tournament.setTargetSize(targetSize);
        tournament.setGroupSize(groupSize);
        tournament.setFogMode(fogMode);
        tournament.setHostGuildId(hostGuildId);
        tournament.setCreatedBy(createdBy);
        tournament.setCreatedAt(Instant.now());
        return tournaments.save(tournament);
    }

    public Optional<Tournament> find(long id) {
        if (isUnavailable()) return Optional.empty();
        return tournaments.findById(id);
    }

    public List<Tournament> listActive() {
        if (isUnavailable()) return List.of();
        return tournaments.findAllByStatusNotOrderByIdAsc(TournamentStatus.ARCHIVED);
    }

    @Transactional
    public Optional<Tournament> update(long id, Consumer<Tournament> change) {
        Optional<Tournament> tournament = tournaments.lockById(id);
        tournament.ifPresent(change);
        return tournament.map(tournaments::save);
    }

    public List<TournamentEntry> entries(long id) {
        return entries.findAllByTournamentIdOrderByJoinedAtAscIdAsc(id);
    }

    public List<TournamentEntry> activeEntries(long id) {
        return entries(id).stream().filter(entry -> !entry.isWaitlisted()).toList();
    }

    public List<TournamentEntry> waitlist(long id) {
        return entries(id).stream().filter(TournamentEntry::isWaitlisted).toList();
    }

    @Transactional
    public JoinResult join(long id, String userId) {
        Tournament tournament = tournaments.lockById(id).orElse(null);
        if (tournament == null) return new JoinResult(JoinOutcome.NOT_FOUND, 0, 0);
        List<TournamentEntry> all = entries(id);
        Optional<TournamentEntry> existing =
                all.stream().filter(entry -> entry.getUserId().equals(userId)).findFirst();
        if (existing.isPresent()) {
            TournamentEntry entry = existing.get();
            return entry.isWaitlisted()
                    ? new JoinResult(JoinOutcome.ALREADY_WAITLISTED, activeCount(all), waitlistPosition(all, userId))
                    : new JoinResult(JoinOutcome.ALREADY_JOINED, activeCount(all), 0);
        }
        if (!tournament.getStatus().acceptsSignups()) {
            return new JoinResult(JoinOutcome.CLOSED, activeCount(all), 0);
        }
        int active = activeCount(all);
        boolean waitlisted = active >= tournament.getTargetSize();
        TournamentEntry entry = entries.save(new TournamentEntry(id, userId, Instant.now(), waitlisted, false));
        if (!waitlisted) return new JoinResult(JoinOutcome.JOINED, active + 1, 0);
        all = new ArrayList<>(all);
        all.add(entry);
        return new JoinResult(JoinOutcome.WAITLISTED, active, waitlistPosition(all, userId));
    }

    @Transactional
    public LeaveResult leave(long id, String userId) {
        Tournament tournament = tournaments.lockById(id).orElse(null);
        if (tournament == null) return LeaveResult.refused("This tournament no longer exists.");
        if (!tournament.getStatus().allowsRosterChanges()) {
            return LeaveResult.refused("The games are already being created. Please contact a tournament organizer.");
        }
        TournamentEntry entry = entries.findByTournamentIdAndUserId(id, userId).orElse(null);
        if (entry == null) return new LeaveResult(false, false, null, null);
        entries.delete(entry);
        if (entry.isWaitlisted()) return new LeaveResult(true, false, null, null);
        String promoted = promoteFirstWaitlisted(id, entry.getGroupNumber());
        if (promoted == null && entry.getGroupNumber() != null && tournament.getStatus() == TournamentStatus.GROUPED) {
            tournament.setStatus(TournamentStatus.CLOSED);
            tournaments.save(tournament);
        }
        return new LeaveResult(true, true, promoted, null);
    }

    @Transactional
    public Optional<String> promoteNext(long id) {
        Tournament tournament = tournaments.lockById(id).orElse(null);
        if (tournament == null || !tournament.getStatus().allowsRosterChanges()) return Optional.empty();
        String promoted = promoteFirstWaitlisted(id, null);
        if (promoted != null && tournament.getStatus() == TournamentStatus.GROUPED) {
            tournament.setStatus(TournamentStatus.CLOSED);
            tournaments.save(tournament);
        }
        return Optional.ofNullable(promoted);
    }

    private String promoteFirstWaitlisted(long id, Integer groupNumber) {
        TournamentEntry next = entries(id).stream()
                .filter(TournamentEntry::isWaitlisted)
                .findFirst()
                .orElse(null);
        if (next == null) return null;
        next.setWaitlisted(false);
        next.setGroupNumber(groupNumber);
        entries.save(next);
        return next.getUserId();
    }

    @Transactional
    public BulkAddResult bulkAdd(long id, Collection<String> userIds) {
        Tournament tournament = tournaments.lockById(id).orElse(null);
        if (tournament == null) return new BulkAddResult(List.of(), List.of(), List.of(), "Tournament not found.");
        if (!tournament.getStatus().allowsRosterChanges()) {
            return new BulkAddResult(List.of(), List.of(), List.of(), "Games are already being created.");
        }
        Map<String, TournamentEntry> existing = new LinkedHashMap<>();
        for (TournamentEntry entry : entries(id)) {
            existing.put(entry.getUserId(), entry);
        }
        List<String> added = new ArrayList<>();
        List<String> promoted = new ArrayList<>();
        List<String> alreadyIn = new ArrayList<>();
        for (String userId : userIds) {
            TournamentEntry entry = existing.get(userId);
            if (entry == null) {
                entries.save(new TournamentEntry(id, userId, Instant.now(), false, true));
                added.add(userId);
            } else if (entry.isWaitlisted()) {
                entry.setWaitlisted(false);
                entry.setAddedByAdmin(true);
                entries.save(entry);
                promoted.add(userId);
            } else {
                alreadyIn.add(userId);
            }
        }
        if ((!added.isEmpty() || !promoted.isEmpty()) && tournament.getStatus() == TournamentStatus.GROUPED) {
            tournament.setStatus(TournamentStatus.CLOSED);
            tournaments.save(tournament);
        }
        return new BulkAddResult(added, promoted, alreadyIn, null);
    }

    @Transactional
    public LeaveResult removeByAdmin(long id, String userId) {
        return leave(id, userId);
    }

    @Transactional
    public void saveGroups(long id, Map<String, Integer> groupByUserId, long seed) {
        Tournament tournament = tournaments.lockById(id).orElseThrow();
        for (TournamentEntry entry : entries(id)) {
            entry.setGroupNumber(entry.isWaitlisted() ? null : groupByUserId.get(entry.getUserId()));
            entries.save(entry);
        }
        tournament.setGroupSeed(seed);
        tournament.setGameMastersByGroupJson(null);
        if (tournament.getStatus() == TournamentStatus.GROUPED) tournament.setStatus(TournamentStatus.CLOSED);
        tournaments.save(tournament);
    }

    @Transactional
    public Optional<String> swap(long id, String firstUserId, String secondUserId) {
        Tournament tournament = tournaments.lockById(id).orElseThrow();
        if (!tournament.getStatus().allowsRosterChanges()) return Optional.of("Games are already being created.");
        TournamentEntry first =
                entries.findByTournamentIdAndUserId(id, firstUserId).orElse(null);
        TournamentEntry second =
                entries.findByTournamentIdAndUserId(id, secondUserId).orElse(null);
        if (first == null || second == null || first.isWaitlisted() || second.isWaitlisted()) {
            return Optional.of("Both players must have a seat in this tournament.");
        }
        if (first.getGroupNumber() == null || second.getGroupNumber() == null) {
            return Optional.of("Generate groups first.");
        }
        Integer firstGroup = first.getGroupNumber();
        first.setGroupNumber(second.getGroupNumber());
        second.setGroupNumber(firstGroup);
        entries.save(first);
        entries.save(second);
        return Optional.empty();
    }

    @Transactional
    public Optional<String> approveGroups(long id) {
        Tournament tournament = tournaments.lockById(id).orElseThrow();
        if (!tournament.getStatus().allowsRosterChanges()) return Optional.of("Games are already being created.");
        List<TournamentEntry> active = activeEntries(id);
        if (active.isEmpty()) return Optional.of("Nobody has signed up yet.");
        long unassigned =
                active.stream().filter(entry -> entry.getGroupNumber() == null).count();
        if (unassigned > 0) {
            return Optional.of(unassigned + " player(s) have no group yet. Generate or reshuffle the groups first.");
        }
        tournament.setStatus(TournamentStatus.GROUPED);
        tournaments.save(tournament);
        return Optional.empty();
    }

    @Transactional
    public void markGameCreated(long id, int groupNumber, String gameName) {
        for (TournamentEntry entry : entries(id)) {
            if (Objects.equals(entry.getGroupNumber(), groupNumber) && !entry.isWaitlisted()) {
                entry.setGameName(gameName);
                entries.save(entry);
            }
        }
    }

    public static Map<Integer, List<TournamentEntry>> groups(List<TournamentEntry> entries) {
        Map<Integer, List<TournamentEntry>> groups = new TreeMap<>();
        for (TournamentEntry entry : entries) {
            if (entry.isWaitlisted() || entry.getGroupNumber() == null) continue;
            groups.computeIfAbsent(entry.getGroupNumber(), group -> new ArrayList<>())
                    .add(entry);
        }
        return groups;
    }

    @Transactional
    public TournamentSignupPost addSignupPost(long id, String guildId, String channelId, String messageId) {
        return signupPosts.save(new TournamentSignupPost(id, guildId, channelId, messageId));
    }

    public List<TournamentSignupPost> signupPosts(long id) {
        if (isUnavailable()) return List.of();
        return signupPosts.findAllByTournamentIdOrderByIdAsc(id);
    }

    @Transactional
    public void removeSignupPost(long postId) {
        signupPosts.deleteById(postId);
    }

    public List<String> gameMasterIds(long id) {
        return gameMasters.findAllByTournamentIdOrderByIdAsc(id).stream()
                .map(TournamentGameMaster::getUserId)
                .toList();
    }

    @Transactional
    public List<String> addGameMasters(long id, Collection<String> userIds) {
        List<String> added = new ArrayList<>();
        for (String userId : userIds) {
            if (gameMasters.findByTournamentIdAndUserId(id, userId).isPresent()) continue;
            gameMasters.save(new TournamentGameMaster(id, userId));
            added.add(userId);
        }
        return added;
    }

    @Transactional
    public boolean removeGameMaster(long id, String userId) {
        Optional<TournamentGameMaster> gameMaster = gameMasters.findByTournamentIdAndUserId(id, userId);
        gameMaster.ifPresent(gameMasters::delete);
        return gameMaster.isPresent();
    }

    public static Map<Integer, String> gameMastersByGroup(Tournament tournament) {
        return readJson(tournament.getGameMastersByGroupJson(), new TypeReference<TreeMap<Integer, String>>() {});
    }

    @Transactional
    public void saveGameMastersByGroup(long id, Map<Integer, String> assignments) {
        update(id, tournament -> tournament.setGameMastersByGroupJson(writeJson(new TreeMap<>(assignments))));
    }

    public static Map<String, String> gameSetup(Tournament tournament) {
        return readJson(tournament.getGameSetupJson(), new TypeReference<LinkedHashMap<String, String>>() {});
    }

    @Transactional
    public void setGameSetupValue(long id, String optionId, String value) {
        update(id, tournament -> {
            Map<String, String> values = gameSetup(tournament);
            values.put(optionId, value);
            tournament.setGameSetupJson(writeJson(values));
        });
    }

    @Transactional
    public Tournament nextRound(long id, String roundLabel, String createdBy) {
        Tournament previous = tournaments.findById(id).orElseThrow();
        Tournament next = new Tournament();
        next.setSeriesName(previous.getSeriesName());
        next.setRoundLabel(roundLabel);
        next.setPreviousRoundId(previous.getId());
        next.setFogMode(previous.isFogMode());
        next.setHostGuildId(previous.getHostGuildId());
        next.setParticipantRoleId(previous.getParticipantRoleId());
        next.setTargetSize(previous.getTargetSize());
        next.setGroupSize(previous.getGroupSize());
        next.setGamesPerCategory(previous.getGamesPerCategory());
        next.setMatchActiveHours(previous.isMatchActiveHours());
        next.setGameSetupJson(previous.getGameSetupJson());
        next.setSetupSourceGame(previous.getSetupSourceGame());
        next.setSpreadPreviousWinners(true);
        next.setAvoidRematches(true);
        next.setStatus(TournamentStatus.CLOSED);
        next.setCreatedBy(createdBy);
        next.setCreatedAt(Instant.now());
        Tournament saved = tournaments.save(next);
        addGameMasters(saved.getId(), gameMasterIds(id));
        return saved;
    }

    public boolean isRoleUsedByAnotherActiveTournament(String roleId, long id) {
        if (StringUtils.isBlank(roleId)) return false;
        return tournaments.existsByParticipantRoleIdAndStatusNotAndIdNot(roleId, TournamentStatus.ARCHIVED, id);
    }

    public static int waitlistPosition(List<TournamentEntry> entries, String userId) {
        List<TournamentEntry> waitlist = entries.stream()
                .filter(TournamentEntry::isWaitlisted)
                .sorted(Comparator.comparing(TournamentEntry::getJoinedAt).thenComparing(TournamentEntry::getId))
                .toList();
        for (int i = 0; i < waitlist.size(); i++) {
            if (waitlist.get(i).getUserId().equals(userId)) return i + 1;
        }
        return 0;
    }

    private static int activeCount(List<TournamentEntry> entries) {
        return (int) entries.stream().filter(entry -> !entry.isWaitlisted()).count();
    }

    private static <T extends Map<?, ?>> T readJson(String json, TypeReference<T> type) {
        if (StringUtils.isNotBlank(json)) {
            try {
                return JsonMapperManager.basic().readValue(json, type);
            } catch (JacksonException e) {
                BotLogger.error("Could not read tournament settings JSON: " + json, e);
            }
        }
        return JsonMapperManager.basic().readValue("{}", type);
    }

    private static String writeJson(Map<?, ?> values) {
        return JsonMapperManager.basic().writeValueAsString(values);
    }
}
