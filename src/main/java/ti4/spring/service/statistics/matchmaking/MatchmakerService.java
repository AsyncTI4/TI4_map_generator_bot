package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedPlayer;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameLaunchPostService;
import ti4.service.game.CreateGameService;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;
import ti4.spring.service.statistics.UserGameInfoService;

@AllArgsConstructor
@Service
public class MatchmakerService {

    private static final int DEFAULT_MAX_QUEUE_TIME_HOURS = 8;
    private static final int NUMBER_OF_ACTIVE_HOUR_BUCKETS = 6;
    private static final int ACTIVE_HOUR_BUCKET_SIZE = 4;
    private static final int ACTIVE_HOUR_BUCKET_MATCH_THRESHOLD = 3;
    private static final long ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT = 3;
    private static final double SIMILAR_SKILL_DIFFERENCE_THRESHOLD = 2.0;
    private static final double RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD = 4.0;
    private static final int NEW_PLAYER_GAME_THRESHOLD = 3;
    private static final BigDecimal NEW_PLAYER_MATCHMAKING_RATING = BigDecimal.valueOf(20.0);

    private final MatchmakingQueueEntryRepository matchmakingQueueEntryRepository;

    @Transactional
    public void queueUser(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return;
        matchmakingQueueEntryRepository.deleteByUserId(userId);

        MatchmakingQueueEntryEntity entry = new MatchmakingQueueEntryEntity();
        entry.setUserId(userId);
        entry.setQueuedAt(Instant.now());

        matchmakingQueueEntryRepository.save(entry);
    }

    /**
     * Queue a party (leader + members) together under the leader's preferences. Every member shares a {@code partyId}
     * equal to the leader's user id and a single {@code queuedAt}.
     *
     * @return an error message if the party could not be queued (e.g. a member is already queued); empty on success.
     */
    @Transactional
    public Optional<String> queueParty(String leaderId, List<String> memberIds) {
        if (DatabasePersistenceGate.isDisabled()) return Optional.of("Queueing is currently disabled.");

        List<String> allIds = distinctMembers(leaderId, memberIds);

        List<String> alreadyQueued = allIds.stream()
                .filter(id -> !id.equals(leaderId))
                .filter(matchmakingQueueEntryRepository::existsByUserId)
                .toList();
        if (!alreadyQueued.isEmpty()) {
            return Optional.of("These players are already in the queue and must leave it before joining your group: "
                    + alreadyQueued.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(", ")));
        }

        // Replace the leader's own existing entry (solo or stale), then queue everyone together.
        matchmakingQueueEntryRepository.deleteByUserId(leaderId);

        Instant now = Instant.now();
        List<MatchmakingQueueEntryEntity> entries = new ArrayList<>();
        for (String id : allIds) {
            MatchmakingQueueEntryEntity entry = new MatchmakingQueueEntryEntity();
            entry.setUserId(id);
            entry.setQueuedAt(now);
            entry.setPartyId(leaderId);
            entries.add(entry);
        }
        matchmakingQueueEntryRepository.saveAll(entries);
        return Optional.empty();
    }

    public static boolean isQueueingDisabled() {
        return DatabasePersistenceGate.isDisabled();
    }

    public boolean isUserQueued(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return matchmakingQueueEntryRepository.existsByUserId(userId);
    }

    @Transactional
    public boolean leaveQueue(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        Optional<MatchmakingQueueEntryEntity> entry = matchmakingQueueEntryRepository.findByUserId(userId);
        if (entry.isEmpty()) return false;

        String partyId = entry.get().getPartyId();
        if (partyId == null) {
            return matchmakingQueueEntryRepository.deleteByUserId(userId) > 0;
        }

        // A party member leaving removes the whole group; notify the others.
        List<MatchmakingQueueEntryEntity> partyMembers = matchmakingQueueEntryRepository.findAllByPartyId(partyId);
        long deleted = matchmakingQueueEntryRepository.deleteByPartyId(partyId);
        notifyPartyLeft(partyMembers, userId);
        return deleted > 0;
    }

    private void notifyPartyLeft(List<MatchmakingQueueEntryEntity> partyMembers, String leaverId) {
        String message = "Your matchmaking group was removed from the queue because <@" + leaverId + "> left it.";
        for (MatchmakingQueueEntryEntity member : partyMembers) {
            if (member.getUserId().equals(leaverId)) continue;
            User user = JdaService.jda.getUserById(member.getUserId());
            if (user == null) continue;
            MessageHelper.sendMessageToUser(message, user);
        }
    }

    /**
     * Validate that a party can be queued under the leader's preferences: it must fit a chosen game size, every member
     * must be eligible for the chosen pace and under their game limit, and every pair of members must be compatible
     * under the leader's restrictions.
     *
     * @return an error message describing the first problem found, or empty if the party is valid.
     */
    public Optional<String> validateParty(String leaderId, List<String> memberIds) {
        List<String> allIds = distinctMembers(leaderId, memberIds);
        UserSettings leaderSettings = UserSettingsManager.get(leaderId);

        int largestChosenPlayerCount = leaderSettings.getMatchmakingPlayerCounts().stream()
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        if (allIds.size() > largestChosenPlayerCount) {
            return Optional.of("Your group has " + allIds.size()
                    + " players, which is larger than the biggest player count you selected ("
                    + largestChosenPlayerCount + "). Pick fewer players or allow a larger game size.");
        }

        Optional<String> paceProblem = validatePaceEligibility(allIds, leaderSettings);
        if (paceProblem.isPresent()) return paceProblem;

        Optional<String> gameLimitProblem = validateGameLimits(allIds);
        if (gameLimitProblem.isPresent()) return gameLimitProblem;

        Map<String, PlayerMatchData> dataById = buildValidationData(allIds, leaderSettings);
        for (int i = 0; i < allIds.size(); i++) {
            for (int j = i + 1; j < allIds.size(); j++) {
                Optional<String> reason =
                        incompatibilityReason(dataById.get(allIds.get(i)), dataById.get(allIds.get(j)), false);
                if (reason.isPresent()) return reason;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validatePaceEligibility(List<String> userIds, UserSettings leaderSettings) {
        UserGameInfoService userGameInfoService = UserGameInfoService.get();
        for (String pace : leaderSettings.getMatchmakingPaces()) {
            Integer requirement = MatchmakingOptions.PACE_RESTRICTION_TO_GAME_DAYS_TO_COMPLETE_REQUIREMENT.get(pace);
            if (requirement == null) continue;
            for (String id : userIds) {
                if (!userGameInfoService.hasCompletedGameInDays(id, requirement)) {
                    return Optional.of("<@" + id + "> isn't eligible for the \"" + pace
                            + "\" pace (it requires a game completed within " + requirement + " days)."
                            + " Deselect the \"" + pace + "\" pace to queue with that player.");
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validateGameLimits(List<String> userIds) {
        for (String id : userIds) {
            ManagedPlayer managedPlayer = GameManager.getManagedPlayer(id);
            if (managedPlayer == null) continue;
            if (UserGameInfoService.isOverStandardGameLimit(managedPlayer)) {
                return Optional.of("<@" + id + "> is at their game limit and can't join more games right now.");
            }
            UserSettings ownSettings = UserSettingsManager.get(id);
            int ongoing = UserGameInfoService.countOngoingGamesThatAffectJoinLimit(managedPlayer);
            if (ownSettings.getGameLimit() > 0 && ongoing >= ownSettings.getGameLimit()) {
                return Optional.of("<@" + id + "> is under a personal game limit and can't join more games right now.");
            }
        }
        return Optional.empty();
    }

    private static List<String> distinctMembers(String leaderId, List<String> memberIds) {
        List<String> allIds = new ArrayList<>();
        allIds.add(leaderId);
        for (String id : memberIds) {
            if (!allIds.contains(id)) allIds.add(id);
        }
        return allIds;
    }

    private static int getHours(String maxQueueTime) {
        if (maxQueueTime == null) return DEFAULT_MAX_QUEUE_TIME_HOURS;
        return MatchmakingOptions.MAX_QUEUE_TIME_OPTIONS_TO_HOURS.get(maxQueueTime.trim());
    }

    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) return;

        List<MatchmakingQueueEntryEntity> entries = matchmakingQueueEntryRepository.findAllByOrderByQueuedAtAsc();
        Map<MatchmakingQueueEntryEntity, UserSettings> settingsByCandidate = getUserSettings(entries);
        List<MatchmakingQueueEntryEntity> candidates =
                cleanAndRemoveExpiredEntries(entries, settingsByCandidate, Instant.now());

        Map<MatchmakingQueueEntryEntity, PlayerMatchData> matchData = buildMatchData(candidates, settingsByCandidate);
        List<Unit> units = buildUnits(candidates);

        List<MatchedGame> gamesToCreate = new ArrayList<>();
        Set<MatchmakingQueueEntryEntity> playersAddedToGames = new HashSet<>();

        for (String playerCountOption : MatchmakingOptions.getPlayerCountOptionsDescending()) {
            for (String victoryPointGoalOption : MatchmakingOptions.getShuffledVictoryPointOptions()) {
                for (String expansionOption : MatchmakingOptions.getShuffledExpansionsOptions()) {
                    for (String paceOption : MatchmakingOptions.getShuffledPaceRestrictions()) {
                        matchAndCollect(
                                units,
                                playersAddedToGames,
                                gamesToCreate,
                                playerCountOption,
                                victoryPointGoalOption,
                                expansionOption,
                                paceOption,
                                settingsByCandidate,
                                matchData);
                    }
                }
            }
        }

        if (!playersAddedToGames.isEmpty()) {
            matchmakingQueueEntryRepository.deleteAllInBatch(playersAddedToGames);
        }

        postMatchedGroupsToMakingNewGamesForum(gamesToCreate);
    }

    /**
     * Greedily fills games for one (playerCount, victoryPoint, expansion, pace) combination. Works on units (a party or
     * a single solo player) so a party is always added or skipped as a whole. Solo-only queues behave as units of size
     * one, reproducing the original individual matching.
     */
    private void matchAndCollect(
            List<Unit> units,
            Set<MatchmakingQueueEntryEntity> playersAddedToGames,
            List<MatchedGame> gamesToCreate,
            String playerCountOption,
            String victoryPointGoalOption,
            String expansionOption,
            String paceOption,
            Map<MatchmakingQueueEntryEntity, UserSettings> settingsByCandidate,
            Map<MatchmakingQueueEntryEntity, PlayerMatchData> matchData) {
        int playerCount = Integer.parseInt(playerCountOption);

        List<Unit> remaining = units.stream()
                .filter(unit -> unit.members().stream().noneMatch(playersAddedToGames::contains))
                .filter(unit -> unit.size() <= playerCount)
                .filter(unit -> {
                    UserSettings settings = settingsByCandidate.get(unit.representative());
                    return settings.getMatchmakingPlayerCounts().contains(playerCountOption)
                            && settings.getMatchmakingVictoryPointGoals().contains(victoryPointGoalOption)
                            && settings.getMatchmakingExpansions().contains(expansionOption)
                            && settings.getMatchmakingPaces().contains(paceOption);
                })
                .sorted(Comparator.comparing((Unit unit) -> getHours(
                                settingsByCandidate.get(unit.representative()).getMatchmakingMaxQueueTime()))
                        .reversed()
                        .thenComparing(unit -> unit.representative().getQueuedAt()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Greedy grouping: seed from the front (longest queue time, then longest-waiting first),
        // then fill the game with the first compatible units found.
        while (totalPlayers(remaining) >= playerCount) {
            Unit seed = remaining.getFirst();
            List<MatchmakingQueueEntryEntity> group = new ArrayList<>(seed.members());
            List<Unit> groupUnits = new ArrayList<>();
            groupUnits.add(seed);

            for (Unit unit : remaining) {
                if (groupUnits.contains(unit)) continue;
                if (group.size() == playerCount) break;
                if (group.size() + unit.size() > playerCount) continue;
                if (unitCompatibleWithGroup(unit, group, settingsByCandidate, matchData)) {
                    group.addAll(unit.members());
                    groupUnits.add(unit);
                }
            }

            if (group.size() == playerCount) {
                List<String> restrictions = group.stream()
                        .flatMap(member -> settingsByCandidate.get(member).getMatchmakingRestrictions().stream())
                        .distinct()
                        .sorted()
                        .toList();
                gamesToCreate.add(new MatchedGame(
                        new ArrayList<>(group),
                        playerCountOption,
                        victoryPointGoalOption,
                        expansionOption,
                        paceOption,
                        restrictions));
                playersAddedToGames.addAll(group);
                remaining.removeAll(groupUnits);
            } else {
                // This unit can't be completed into a full game right now; skip its seed.
                remaining.remove(seed);
            }
        }
    }

    private boolean unitCompatibleWithGroup(
            Unit unit,
            List<MatchmakingQueueEntryEntity> group,
            Map<MatchmakingQueueEntryEntity, UserSettings> settingsByCandidate,
            Map<MatchmakingQueueEntryEntity, PlayerMatchData> matchData) {
        for (MatchmakingQueueEntryEntity newMember : unit.members()) {
            for (MatchmakingQueueEntryEntity existing : group) {
                boolean relaxed = isHalfQueueTimePassed(existing, settingsByCandidate)
                        || isHalfQueueTimePassed(newMember, settingsByCandidate);
                if (incompatibilityReason(matchData.get(existing), matchData.get(newMember), relaxed)
                        .isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int totalPlayers(List<Unit> units) {
        return units.stream().mapToInt(Unit::size).sum();
    }

    private List<Unit> buildUnits(List<MatchmakingQueueEntryEntity> candidates) {
        Map<String, List<MatchmakingQueueEntryEntity>> partyMembers = new HashMap<>();
        List<Unit> units = new ArrayList<>();
        for (MatchmakingQueueEntryEntity candidate : candidates) {
            if (candidate.getPartyId() != null) {
                partyMembers
                        .computeIfAbsent(candidate.getPartyId(), key -> new ArrayList<>())
                        .add(candidate);
            } else {
                units.add(new Unit(List.of(candidate)));
            }
        }
        partyMembers.values().forEach(members -> units.add(new Unit(List.copyOf(members))));
        return units;
    }

    /**
     * The first incompatibility between two players under their (effective) restrictions, or empty if they are
     * compatible. Used both for cron-time matching (the reason is ignored) and submit-time party validation (the reason
     * is surfaced to the leader).
     */
    private Optional<String> incompatibilityReason(PlayerMatchData a, PlayerMatchData b, boolean relaxed) {
        if (a.avoidList().contains(b.userId())) {
            return Optional.of("<@" + a.userId() + "> has <@" + b.userId() + "> on their avoid list."
                    + " Remove them from your avoid list (Additional Settings) to queue with that player.");
        }
        if (b.avoidList().contains(a.userId())) {
            return Optional.of("<@" + b.userId() + "> has <@" + a.userId() + "> on their avoid list."
                    + " Remove them from your avoid list (Additional Settings) to queue with that player.");
        }

        String aRestrictions = a.restrictionsCsv();
        String bRestrictions = b.restrictionsCsv();

        if (MatchmakingOptions.wantsTigl(aRestrictions) != MatchmakingOptions.wantsTigl(bRestrictions)) {
            return Optional.of("<@" + a.userId() + "> and <@" + b.userId() + "> disagree on the \""
                    + MatchmakingOptions.TIGL_OPTION + "\" restriction."
                    + removeOptionHint(MatchmakingOptions.TIGL_OPTION));
        }

        Optional<String> roleReason = roleViolationReason(aRestrictions, a.roleNames(), b.roleNames(), b.userId());
        if (roleReason.isPresent()) return roleReason;
        roleReason = roleViolationReason(bRestrictions, b.roleNames(), a.roleNames(), a.userId());
        if (roleReason.isPresent()) return roleReason;

        boolean aIsNew = a.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        boolean bIsNew = b.completedGames() < NEW_PLAYER_GAME_THRESHOLD;
        if (bIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(aRestrictions)) {
            return Optional.of("<@" + b.userId() + "> is a new async player."
                    + removeOptionHint(MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION));
        }
        if (aIsNew && MatchmakingOptions.wantsToAvoidNewPlayers(bRestrictions)) {
            return Optional.of("<@" + a.userId() + "> is a new async player."
                    + removeOptionHint(MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION));
        }

        if (MatchmakingOptions.wantsSimilarActiveHours(aRestrictions)
                || MatchmakingOptions.wantsSimilarActiveHours(bRestrictions)) {
            long sharedBuckets = a.activeHourBuckets().stream()
                    .filter(b.activeHourBuckets()::contains)
                    .count();
            if (sharedBuckets < ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT) {
                return Optional.of(
                        "<@" + a.userId() + "> and <@" + b.userId() + "> don't have similar enough active hours."
                                + removeOptionHint(MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION));
            }
        }

        if (MatchmakingOptions.wantsSimilarPlayerSkill(aRestrictions)
                || MatchmakingOptions.wantsSimilarPlayerSkill(bRestrictions)) {
            BigDecimal aRating = aIsNew ? NEW_PLAYER_MATCHMAKING_RATING : a.rating();
            BigDecimal bRating = bIsNew ? NEW_PLAYER_MATCHMAKING_RATING : b.rating();
            double tolerance =
                    relaxed ? RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD : SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
            if (aRating.subtract(bRating).abs().compareTo(BigDecimal.valueOf(tolerance)) > 0) {
                return Optional.of(
                        "<@" + a.userId() + "> and <@" + b.userId() + "> don't have similar enough skill ratings."
                                + removeOptionHint(MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION));
            }
        }

        return Optional.empty();
    }

    private static String removeOptionHint(String restrictionOption) {
        return " Remove the \"" + restrictionOption + "\" queue option to queue with that player.";
    }

    private static Optional<String> roleViolationReason(
            String chooserRestrictionsCsv, Set<String> chooserRoleNames, Set<String> otherRoleNames, String otherId) {
        if (chooserRoleNames.contains(MatchmakingOptions.FLOATERS_ROLE_NAME)
                && MatchmakingOptions.wantsOnlyFloaters(chooserRestrictionsCsv)
                && !otherRoleNames.contains(MatchmakingOptions.FLOATERS_ROLE_NAME)) {
            return Optional.of("<@" + otherId + "> doesn't have the " + MatchmakingOptions.FLOATERS_ROLE_NAME + " role."
                    + removeOptionHint(MatchmakingOptions.ONLY_MATCH_FLOATERS_OPTION));
        }
        if (chooserRoleNames.contains(MatchmakingOptions.WARRIORS_ROLE_NAME)
                && MatchmakingOptions.wantsOnlyWarriors(chooserRestrictionsCsv)
                && !otherRoleNames.contains(MatchmakingOptions.WARRIORS_ROLE_NAME)) {
            return Optional.of("<@" + otherId + "> doesn't have the " + MatchmakingOptions.WARRIORS_ROLE_NAME + " role."
                    + removeOptionHint(MatchmakingOptions.ONLY_MATCH_WARRIORS_OPTION));
        }
        return Optional.empty();
    }

    private boolean isHalfQueueTimePassed(
            MatchmakingQueueEntryEntity player,
            Map<MatchmakingQueueEntryEntity, UserSettings> userSettingsByCandidate) {
        double maxHours = getHours(userSettingsByCandidate.get(player).getMatchmakingMaxQueueTime());
        double hoursWaited =
                Duration.between(player.getQueuedAt(), Instant.now()).toMinutes() / 60.0;
        return hoursWaited >= maxHours / SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
    }

    /**
     * Build per-candidate matching data. Preferences (restrictions, avoid list) come from the effective settings (the
     * leader's, for party members), while profile attributes (active hours, rating, completed games, roles) always come
     * from the candidate's own account.
     */
    private Map<MatchmakingQueueEntryEntity, PlayerMatchData> buildMatchData(
            List<MatchmakingQueueEntryEntity> candidates,
            Map<MatchmakingQueueEntryEntity, UserSettings> settingsByCandidate) {
        Set<String> userIds =
                candidates.stream().map(MatchmakingQueueEntryEntity::getUserId).collect(Collectors.toSet());
        Map<String, BigDecimal> ratings = MatchmakingRatingEventService.get().getPlayerRatings(userIds);
        Guild guild = JdaService.guildPrimary;

        Map<MatchmakingQueueEntryEntity, PlayerMatchData> matchData = new HashMap<>();
        for (MatchmakingQueueEntryEntity candidate : candidates) {
            UserSettings effectiveSettings = settingsByCandidate.get(candidate);
            UserSettings ownSettings =
                    candidate.getPartyId() != null ? UserSettingsManager.get(candidate.getUserId()) : effectiveSettings;
            matchData.put(
                    candidate,
                    new PlayerMatchData(
                            candidate.getUserId(),
                            toCsv(effectiveSettings.getMatchmakingRestrictions()),
                            effectiveSettings.getMatchmakingAvoidList(),
                            ratings.getOrDefault(candidate.getUserId(), NEW_PLAYER_MATCHMAKING_RATING),
                            computeActiveHourBuckets(ownSettings.getActiveHoursAsIntegers()),
                            completedGames(candidate.getUserId()),
                            roleNames(guild, candidate.getUserId())));
        }
        return matchData;
    }

    private Map<String, PlayerMatchData> buildValidationData(List<String> userIds, UserSettings leaderSettings) {
        Map<String, BigDecimal> ratings = MatchmakingRatingEventService.get().getPlayerRatings(new HashSet<>(userIds));
        Guild guild = JdaService.guildPrimary;
        String leaderRestrictionsCsv = toCsv(leaderSettings.getMatchmakingRestrictions());

        Map<String, PlayerMatchData> dataById = new HashMap<>();
        for (String id : userIds) {
            UserSettings ownSettings = UserSettingsManager.get(id);
            dataById.put(
                    id,
                    new PlayerMatchData(
                            id,
                            leaderRestrictionsCsv,
                            ownSettings.getMatchmakingAvoidList(),
                            ratings.getOrDefault(id, NEW_PLAYER_MATCHMAKING_RATING),
                            computeActiveHourBuckets(ownSettings.getActiveHoursAsIntegers()),
                            completedGames(id),
                            roleNames(guild, id)));
        }
        return dataById;
    }

    private static int completedGames(String userId) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        if (managedPlayer == null) return 0;
        return UserGameInfoService.countCompletedGamesThatAffectJoinLimit(managedPlayer);
    }

    private static Set<String> roleNames(Guild guild, String userId) {
        Member member = guild == null ? null : guild.getMemberById(userId);
        return member == null ? Set.of() : MatchmakingOptions.getHeldOnlyMatchRoleNames(guild, member);
    }

    private Set<Integer> computeActiveHourBuckets(Set<Integer> activeHours) {
        Set<Integer> matchedBuckets = new HashSet<>();
        for (int i = 0; i < NUMBER_OF_ACTIVE_HOUR_BUCKETS; i++) {
            int startHour = i * ACTIVE_HOUR_BUCKET_SIZE;
            int endHour = startHour + ACTIVE_HOUR_BUCKET_SIZE - 1;
            if (getBucketScore(activeHours, startHour, endHour) >= ACTIVE_HOUR_BUCKET_MATCH_THRESHOLD) {
                matchedBuckets.add(i);
            }
        }
        return matchedBuckets;
    }

    private int getBucketScore(Set<Integer> activeHours, int startInclusive, int endInclusive) {
        int score = 0;
        for (int hour : activeHours) {
            if (hour >= startInclusive && hour <= endInclusive) {
                score++;
            }
        }
        return score;
    }

    @NonNull
    private List<MatchmakingQueueEntryEntity> cleanAndRemoveExpiredEntries(
            List<MatchmakingQueueEntryEntity> entries,
            Map<MatchmakingQueueEntryEntity, UserSettings> userSettingsByEntry,
            Instant now) {
        List<MatchmakingQueueEntryEntity> expired = entries.stream()
                .filter(entry -> entry.getQueuedAt()
                        .plus(Duration.ofHours(
                                getHours(userSettingsByEntry.get(entry).getMatchmakingMaxQueueTime())))
                        .isBefore(now))
                .toList();
        if (!expired.isEmpty()) {
            BotLogger.info("Expiring " + expired.size() + " matchmaking queue entries.");
            matchmakingQueueEntryRepository.deleteAllInBatch(expired);
            String expiryMessage =
                    "The matchmaking service wasn't able to find you a game in the time frame you selected. "
                            + "Queue again when ready and consider being open to additional game types or longer wait "
                            + "times.";
            for (MatchmakingQueueEntryEntity entry : expired) {
                userSettingsByEntry.remove(entry);

                User user = JdaService.jda.getUserById(entry.getUserId());
                if (user == null) continue;

                MessageHelper.sendMessageToUser(expiryMessage, user);
            }
        }
        return entries.stream().filter(entry -> !expired.contains(entry)).toList();
    }

    /**
     * Effective settings per entry: a party member uses the leader's settings (party id == leader id), a solo player
     * uses their own.
     */
    private Map<MatchmakingQueueEntryEntity, UserSettings> getUserSettings(List<MatchmakingQueueEntryEntity> entries) {
        return entries.stream().collect(Collectors.toMap(entry -> entry, entry -> {
            String settingsOwner = entry.getPartyId() != null ? entry.getPartyId() : entry.getUserId();
            return UserSettingsManager.get(settingsOwner);
        }));
    }

    private static String toCsv(List<String> values) {
        return String.join(",", values);
    }

    private void postMatchedGroupsToMakingNewGamesForum(List<MatchedGame> gamesToCreate) {
        Guild guild = JdaService.guildPrimary;
        if (gamesToCreate.isEmpty() || guild == null) return;

        List<ForumChannel> forums =
                guild.getForumChannelsByName(CreateGameLaunchPostService.MAKING_NEW_GAMES_CHANNEL, true);
        if (forums.isEmpty()) {
            BotLogger.error("MatchmakerService could not find a thread container named #"
                    + CreateGameLaunchPostService.MAKING_NEW_GAMES_CHANNEL + ".");
            return;
        }
        IThreadContainer threadContainer = forums.getFirst();

        for (MatchedGame game : gamesToCreate) {
            List<MatchmakingQueueEntryEntity> queueEntries = game.entries();
            List<Member> members = queueEntries.stream()
                    .map(entry -> guild.getMemberById(entry.getUserId()))
                    .filter(Objects::nonNull)
                    .toList();
            if (members.size() != queueEntries.size()) continue;

            String gameFunName = CreateGameService.autoGenerateGameName();
            String threadTitle = "Matchmaker Game: " + gameFunName.replace(":", "");
            String setupMessage = game.describeSetup();
            threadContainer.createThreadChannel(threadTitle).queue(thread -> {
                MessageHelper.sendMessageToChannel(thread, setupMessage);
                CreateGameLaunchPostService.postLaunchButtons(thread, members, gameFunName);
            });
        }
    }

    /** A party (members sharing a party id) or a single solo player, matched into a game as a whole. */
    private record Unit(List<MatchmakingQueueEntryEntity> members) {
        private int size() {
            return members.size();
        }

        private MatchmakingQueueEntryEntity representative() {
            return members.getFirst();
        }
    }

    /** Pre-computed matching inputs for one player. */
    private record PlayerMatchData(
            String userId,
            String restrictionsCsv,
            List<String> avoidList,
            BigDecimal rating,
            Set<Integer> activeHourBuckets,
            int completedGames,
            Set<String> roleNames) {}

    private record MatchedGame(
            List<MatchmakingQueueEntryEntity> entries,
            String playerCount,
            String victoryPointGoal,
            String expansion,
            String pace,
            List<String> restrictions) {

        private String describeSetup() {
            String restrictionsText = restrictions.isEmpty() ? "None" : String.join(", ", restrictions);
            return "The players were matched on the following game setup:\n"
                    + "- **Player count:** " + playerCount + "\n"
                    + "- **Victory point goal:** " + victoryPointGoal + "\n"
                    + "- **Expansion:** " + expansion + "\n"
                    + "- **Pace:** " + pace + "\n"
                    + "- **Restrictions:** " + restrictionsText;
        }
    }

    public static MatchmakerService get() {
        return SpringContext.getBean(MatchmakerService.class);
    }
}
