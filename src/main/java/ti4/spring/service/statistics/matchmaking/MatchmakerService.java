package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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
    private static final int NEW_PLAYER_GAME_THRESHOLD = 1;

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

    public boolean isQueueingDisabled() {
        return DatabasePersistenceGate.isDisabled();
    }

    public boolean isUserQueued(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return matchmakingQueueEntryRepository.existsByUserId(userId);
    }

    @Transactional
    public boolean leaveQueue(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return matchmakingQueueEntryRepository.deleteByUserId(userId) > 0;
    }

    private static int getHours(String maxQueueTime) {
        if (maxQueueTime == null) return DEFAULT_MAX_QUEUE_TIME_HOURS;
        return MatchmakingOptions.MAX_QUEUE_TIME_OPTIONS_TO_HOURS.get(maxQueueTime.trim());
    }

    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) return;

        List<MatchmakingQueueEntryEntity> entries = matchmakingQueueEntryRepository.findAllByOrderByQueuedAtAsc();
        Map<MatchmakingQueueEntryEntity, UserSettings> candidateToUserSettings = getUserSettings(entries);
        List<MatchmakingQueueEntryEntity> candidates =
                cleanAndRemoveExpiredEntries(entries, candidateToUserSettings, Instant.now());

        Map<String, BigDecimal> playerRatings = getPlayerRatings(candidates);
        BigDecimal averageRating = playerRatings.isEmpty()
                ? BigDecimal.ZERO
                : playerRatings.values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(playerRatings.size()), MathContext.DECIMAL64);
        Map<MatchmakingQueueEntryEntity, Set<Integer>> playersToActiveHourBuckets =
                getActiveHourBuckets(candidates, candidateToUserSettings);
        Map<MatchmakingQueueEntryEntity, Integer> playersToCompletedGameCounts = getCompletedGameCounts(candidates);
        Map<MatchmakingQueueEntryEntity, Set<String>> playersToRoleIds = getRoleIds(candidates);
        List<List<MatchmakingQueueEntryEntity>> gamesToCreate = new ArrayList<>();
        Set<MatchmakingQueueEntryEntity> playersAddedToGames = new HashSet<>();

        for (String playerCountOption : MatchmakingOptions.getPlayerCountOptionsDescending()) {
            for (String victoryPointGoalOption : MatchmakingOptions.getShuffledVictoryPointOptions()) {
                for (String expansionOption : MatchmakingOptions.getShuffledExpansionsOptions()) {
                    for (String paceOption : MatchmakingOptions.getShuffledPaceRestrictions()) {
                        matchAndCollect(
                                candidates,
                                playersAddedToGames,
                                gamesToCreate,
                                playerCountOption,
                                victoryPointGoalOption,
                                expansionOption,
                                paceOption,
                                candidateToUserSettings,
                                playersToActiveHourBuckets,
                                playersToCompletedGameCounts,
                                playersToRoleIds,
                                playerRatings,
                                averageRating);
                    }
                }
            }
        }

        if (!playersAddedToGames.isEmpty()) {
            matchmakingQueueEntryRepository.deleteAllInBatch(playersAddedToGames);
        }

        postMatchedGroupsToMakingNewGamesForum(gamesToCreate);
    }

    private static Map<String, BigDecimal> getPlayerRatings(List<MatchmakingQueueEntryEntity> candidates) {
        Set<String> userIds =
                candidates.stream().map(MatchmakingQueueEntryEntity::getUserId).collect(Collectors.toSet());
        return MatchmakingRatingEventService.get().getPlayerRatings(userIds);
    }

    private void matchAndCollect(
            List<MatchmakingQueueEntryEntity> candidates,
            Set<MatchmakingQueueEntryEntity> playersAddedToGames,
            List<List<MatchmakingQueueEntryEntity>> gamesToCreate,
            String playerCountOption,
            String victoryPointGoalOption,
            String expansionOption,
            String paceOption,
            Map<MatchmakingQueueEntryEntity, UserSettings> userSettingsByCandidate,
            Map<MatchmakingQueueEntryEntity, Set<Integer>> playersToActiveHourBuckets,
            Map<MatchmakingQueueEntryEntity, Integer> playersToCompletedGameCounts,
            Map<MatchmakingQueueEntryEntity, Set<String>> playersToRoleIds,
            Map<String, BigDecimal> playerRatings,
            BigDecimal defaultRating) {
        List<MatchmakingQueueEntryEntity> eligible = candidates.stream()
                .filter(candidate -> !playersAddedToGames.contains(candidate))
                .filter(candidate -> userSettingsByCandidate
                        .get(candidate)
                        .getMatchmakingPlayerCounts()
                        .contains(playerCountOption))
                .filter(candidate -> userSettingsByCandidate
                        .get(candidate)
                        .getMatchmakingVictoryPointGoals()
                        .contains(victoryPointGoalOption))
                .filter(candidate -> userSettingsByCandidate
                        .get(candidate)
                        .getMatchmakingExpansions()
                        .contains(expansionOption))
                .filter(candidate -> userSettingsByCandidate
                        .get(candidate)
                        .getMatchmakingPaces()
                        .contains(paceOption))
                .sorted(Comparator.comparing(
                                c -> getHours(userSettingsByCandidate.get(c).getMatchmakingMaxQueueTime()))
                        .reversed())
                .toList();

        // Greedy grouping: seed from the front (longest-waiting players first),
        // then fill the group with the first compatible candidates found.
        int playerCount = Integer.parseInt(playerCountOption);
        List<MatchmakingQueueEntryEntity> remaining = new ArrayList<>(eligible);
        while (remaining.size() >= playerCount) {
            MatchmakingQueueEntryEntity seed = remaining.getFirst();
            List<MatchmakingQueueEntryEntity> group = new ArrayList<>();
            group.add(seed);

            for (MatchmakingQueueEntryEntity candidate : remaining) {
                if (group.contains(candidate)) continue;
                if (group.size() == playerCount) break;

                boolean compatibleWithWholeGroup = group.stream()
                        .allMatch(member -> areCompatible(
                                member,
                                candidate,
                                userSettingsByCandidate,
                                playersToActiveHourBuckets,
                                playersToCompletedGameCounts,
                                playersToRoleIds,
                                playerRatings,
                                defaultRating));

                if (compatibleWithWholeGroup) {
                    group.add(candidate);
                }
            }

            if (group.size() == playerCount) {
                gamesToCreate.add(new ArrayList<>(group));
                playersAddedToGames.addAll(group);
                remaining.removeAll(group);
            } else {
                // This player can't form a compatible group right now; skip them.
                remaining.remove(seed);
            }
        }
    }

    private boolean areCompatible(
            MatchmakingQueueEntryEntity player1,
            MatchmakingQueueEntryEntity player2,
            Map<MatchmakingQueueEntryEntity, UserSettings> userSettingsByCandidate,
            Map<MatchmakingQueueEntryEntity, Set<Integer>> playersToActiveHourBuckets,
            Map<MatchmakingQueueEntryEntity, Integer> playersToCompletedGameCounts,
            Map<MatchmakingQueueEntryEntity, Set<String>> playersToRoleIds,
            Map<String, BigDecimal> playerRatings,
            BigDecimal defaultRating) {
        UserSettings player1Settings = userSettingsByCandidate.get(player1);
        UserSettings player2Settings = userSettingsByCandidate.get(player2);
        String player1Id = player1.getUserId();
        String player2Id = player2.getUserId();
        if (player1Settings.getMatchmakingAvoidList().contains(player2Id)
                || player2Settings.getMatchmakingAvoidList().contains(player1Id)) {
            return false;
        }

        String player1RestrictionsCsv = toCsv(player1Settings.getMatchmakingRestrictions());
        String player2RestrictionsCsv = toCsv(player2Settings.getMatchmakingRestrictions());

        boolean player1WantsTigl = MatchmakingOptions.wantsTigl(player1RestrictionsCsv);
        boolean player2WantsTigl = MatchmakingOptions.wantsTigl(player2RestrictionsCsv);
        if (player1WantsTigl != player2WantsTigl) {
            return false;
        }

        Set<String> player1RoleIds = playersToRoleIds.getOrDefault(player1, Set.of());
        Set<String> player2RoleIds = playersToRoleIds.getOrDefault(player2, Set.of());
        if (violatesRoleRestriction(player1RestrictionsCsv, player1RoleIds, player2RoleIds)
                || violatesRoleRestriction(player2RestrictionsCsv, player2RoleIds, player1RoleIds)) {
            return false;
        }

        int player1CompletedGames = playersToCompletedGameCounts.getOrDefault(player1, 0);
        int player2CompletedGames = playersToCompletedGameCounts.getOrDefault(player2, 0);
        boolean player1IsNew = player1CompletedGames < NEW_PLAYER_GAME_THRESHOLD;
        boolean player2IsNew = player2CompletedGames < NEW_PLAYER_GAME_THRESHOLD;
        if (player2IsNew && MatchmakingOptions.wantsToAvoidNewPlayers(player1RestrictionsCsv)) {
            return false;
        }
        if (player1IsNew && MatchmakingOptions.wantsToAvoidNewPlayers(player2RestrictionsCsv)) {
            return false;
        }

        boolean player1WantsSimilarHours = MatchmakingOptions.wantsSimilarActiveHours(player1RestrictionsCsv);
        boolean player2WantsSimilarHours = MatchmakingOptions.wantsSimilarActiveHours(player2RestrictionsCsv);
        if (player1WantsSimilarHours || player2WantsSimilarHours) {
            Set<Integer> player1ActiveHourBuckets = playersToActiveHourBuckets.getOrDefault(player1, Set.of());
            Set<Integer> player2ActiveHourBuckets = playersToActiveHourBuckets.getOrDefault(player2, Set.of());
            long sharedBuckets = player1ActiveHourBuckets.stream()
                    .filter(player2ActiveHourBuckets::contains)
                    .count();
            if (sharedBuckets < ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT) return false;
        }

        boolean player1WantsSimilarSkill = MatchmakingOptions.wantsSimilarPlayerSkill(player1RestrictionsCsv);
        boolean player2WantsSimilarSkill = MatchmakingOptions.wantsSimilarPlayerSkill(player2RestrictionsCsv);
        if (player1WantsSimilarSkill || player2WantsSimilarSkill) {
            BigDecimal player1Rating = playerRatings.getOrDefault(player1.getUserId(), defaultRating);
            BigDecimal player2Rating = playerRatings.getOrDefault(player2.getUserId(), defaultRating);

            boolean relaxed = isHalfQueueTimePassed(player1, userSettingsByCandidate)
                    || isHalfQueueTimePassed(player2, userSettingsByCandidate);
            double tolerance =
                    relaxed ? RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD : SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
            return player1Rating.subtract(player2Rating).abs().compareTo(BigDecimal.valueOf(tolerance)) <= 0;
        }

        return true;
    }

    private boolean isHalfQueueTimePassed(
            MatchmakingQueueEntryEntity player,
            Map<MatchmakingQueueEntryEntity, UserSettings> userSettingsByCandidate) {
        double maxHours = getHours(userSettingsByCandidate.get(player).getMatchmakingMaxQueueTime());
        double hoursWaited =
                Duration.between(player.getQueuedAt(), Instant.now()).toMinutes() / 60.0;
        return hoursWaited >= maxHours / SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
    }

    private static boolean violatesRoleRestriction(
            String chooserRestrictionsCsv, Set<String> chooserRoleIds, Set<String> otherRoleIds) {
        if (chooserRoleIds.contains(MatchmakingOptions.FLOATERS_ROLE_ID)
                && MatchmakingOptions.wantsOnlyFloaters(chooserRestrictionsCsv)
                && !otherRoleIds.contains(MatchmakingOptions.FLOATERS_ROLE_ID)) {
            return true;
        }
        return chooserRoleIds.contains(MatchmakingOptions.WARRIORS_ROLE_ID)
                && MatchmakingOptions.wantsOnlyWarriors(chooserRestrictionsCsv)
                && !otherRoleIds.contains(MatchmakingOptions.WARRIORS_ROLE_ID);
    }

    private Map<MatchmakingQueueEntryEntity, Set<String>> getRoleIds(List<MatchmakingQueueEntryEntity> candidates) {
        Guild guild = JdaService.guildPrimary;
        Map<MatchmakingQueueEntryEntity, Set<String>> roleIdsByCandidate = new HashMap<>();
        for (MatchmakingQueueEntryEntity candidate : candidates) {
            Member member = guild == null ? null : guild.getMemberById(candidate.getUserId());
            Set<String> roleIds = member == null
                    ? Set.of()
                    : member.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
            roleIdsByCandidate.put(candidate, roleIds);
        }
        return roleIdsByCandidate;
    }

    private Map<MatchmakingQueueEntryEntity, Integer> getCompletedGameCounts(
            List<MatchmakingQueueEntryEntity> candidates) {
        Map<MatchmakingQueueEntryEntity, Integer> completedGameCounts = new HashMap<>();
        for (MatchmakingQueueEntryEntity candidate : candidates) {
            ManagedPlayer managedPlayer = GameManager.getManagedPlayer(candidate.getUserId());
            completedGameCounts.put(
                    candidate, UserGameInfoService.countCompletedGamesThatAffectJoinLimit(managedPlayer));
        }
        return completedGameCounts;
    }

    private Map<MatchmakingQueueEntryEntity, Set<Integer>> getActiveHourBuckets(
            List<MatchmakingQueueEntryEntity> eligible,
            Map<MatchmakingQueueEntryEntity, UserSettings> userSettingsByCandidate) {
        Map<MatchmakingQueueEntryEntity, Set<Integer>> playerToBucketsMap = new HashMap<>();

        for (MatchmakingQueueEntryEntity player : eligible) {
            Set<Integer> activeHours = userSettingsByCandidate.get(player).getActiveHoursAsIntegers();

            Set<Integer> matchedBuckets = new HashSet<>();
            for (int i = 0; i < NUMBER_OF_ACTIVE_HOUR_BUCKETS; i++) {
                int startHour = i * ACTIVE_HOUR_BUCKET_SIZE;
                int endHour = startHour + ACTIVE_HOUR_BUCKET_SIZE - 1;

                int score = getBucketScore(activeHours, startHour, endHour);
                if (score >= ACTIVE_HOUR_BUCKET_MATCH_THRESHOLD) {
                    matchedBuckets.add(i);
                }
            }
            if (!matchedBuckets.isEmpty()) {
                playerToBucketsMap.put(player, matchedBuckets);
            }
        }

        return playerToBucketsMap;
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

    private Map<MatchmakingQueueEntryEntity, UserSettings> getUserSettings(List<MatchmakingQueueEntryEntity> entries) {
        return entries.stream()
                .collect(Collectors.toMap(entry -> entry, entry -> UserSettingsManager.get(entry.getUserId())));
    }

    private static String toCsv(List<String> values) {
        return String.join(",", values);
    }

    private void postMatchedGroupsToMakingNewGamesForum(List<List<MatchmakingQueueEntryEntity>> gamesToCreate) {
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

        for (List<MatchmakingQueueEntryEntity> queueEntries : gamesToCreate) {
            List<Member> members = queueEntries.stream()
                    .map(entry -> guild.getMemberById(entry.getUserId()))
                    .filter(Objects::nonNull)
                    .toList();
            if (members.size() != queueEntries.size()) continue;

            String gameFunName = CreateGameService.autoGenerateGameName();
            String threadTitle = "Matchmaker Game: " + gameFunName.replace(":", "");
            threadContainer
                    .createThreadChannel(threadTitle)
                    .queue(thread -> CreateGameLaunchPostService.postLaunchButtons(thread, members, gameFunName));
        }
    }

    public static MatchmakerService get() {
        return SpringContext.getBean(MatchmakerService.class);
    }
}
