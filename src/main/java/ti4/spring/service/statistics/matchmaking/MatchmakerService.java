package ti4.spring.service.statistics.matchmaking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameLaunchPostService;
import ti4.service.game.CreateGameService;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;

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

    private final MatchmakingQueueEntryRepository matchmakingQueueEntryRepository;

    @Transactional
    public void queueUser(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return;
        matchmakingQueueEntryRepository.deleteByUserId(userId);

        MatchmakingQueueEntryEntity entry = new MatchmakingQueueEntryEntity();
        entry.setUserId(userId);
        entry.setQueuedAtUtc(LocalDateTime.now(ZoneOffset.UTC));

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

    private static int parseHours(String maxQueueTime) {
        if (maxQueueTime == null) return DEFAULT_MAX_QUEUE_TIME_HOURS;
        StringBuilder hours = new StringBuilder();
        for (char c : maxQueueTime.trim().toCharArray()) {
            if (Character.isDigit(c)) hours.append(c);
            else break;
        }
        if (hours.isEmpty()) return DEFAULT_MAX_QUEUE_TIME_HOURS;
        return Integer.parseInt(hours.toString());
    }

    public void processQueue() {
        if (DatabasePersistenceGate.isDisabled()) return;

        List<MatchmakingQueueEntryEntity> entries = matchmakingQueueEntryRepository.findAllByOrderByQueuedAtUtcAsc();
        Map<MatchmakingQueueEntryEntity, UserSettings> candidateToUserSettings = getUserSettings(entries);
        List<MatchmakingQueueEntryEntity> candidates =
                cleanAndRemoveExpiredEntries(entries, candidateToUserSettings, LocalDateTime.now(ZoneOffset.UTC));

        Map<String, Double> playerRatings = getPlayerRatings(candidates);
        double averageRating = playerRatings.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);
        Map<MatchmakingQueueEntryEntity, Set<Integer>> playersToActiveHourBuckets =
                getActiveHourBuckets(candidates, candidateToUserSettings);
        List<List<MatchmakingQueueEntryEntity>> gamesToCreate = new ArrayList<>();
        Set<MatchmakingQueueEntryEntity> playersAddedToGames = new HashSet<>();

        for (String playerCountOption : MatchmakingOptions.getPlayerCountOptionsDescending()) {
            for (String victoryPointGoalOption : MatchmakingOptions.getVictoryPointOptionsDescending()) {
                for (String expansionOption : MatchmakingOptions.getShuffledExpansionsWithBaseIncluded()) {
                    for (String pace : MatchmakingOptions.getPaceRestrictions()) {
                        for (Predicate<String> tiglPredicate : MatchmakingOptions.getTiglRestrictionPredicates()) {
                            matchAndCollect(
                                    candidates,
                                    playersAddedToGames,
                                    gamesToCreate,
                                    playerCountOption,
                                    victoryPointGoalOption,
                                    expansionOption,
                                    pace,
                                    tiglPredicate,
                                    candidateToUserSettings,
                                    playersToActiveHourBuckets,
                                    playerRatings,
                                    averageRating);
                        }
                    }
                }
            }
        }

        if (!playersAddedToGames.isEmpty()) {
            matchmakingQueueEntryRepository.deleteAllInBatch(playersAddedToGames);
        }

        postMatchedGroupsToMakingNewGamesForum(gamesToCreate);
    }

    private static Map<String, Double> getPlayerRatings(List<MatchmakingQueueEntryEntity> candidates) {
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
            String pace,
            Predicate<String> tiglPredicate,
            Map<MatchmakingQueueEntryEntity, UserSettings> userSettingsByCandidate,
            Map<MatchmakingQueueEntryEntity, Set<Integer>> playersToActiveHourBuckets,
            Map<String, Double> playerRatings,
            Double defaultRating) {
        List<MatchmakingQueueEntryEntity> eligible = candidates.stream()
                .filter(c -> !playersAddedToGames.contains(c))
                .filter(c -> userSettingsByCandidate
                        .get(c)
                        .getQueueForGamePlayerCounts()
                        .contains(playerCountOption))
                .filter(c -> userSettingsByCandidate
                        .get(c)
                        .getQueueForGameVictoryPointGoals()
                        .contains(victoryPointGoalOption))
                .filter(c -> userSettingsByCandidate
                        .get(c)
                        .getQueueForGameExpansions()
                        .contains(expansionOption))
                .filter(c -> userSettingsByCandidate
                        .get(c)
                        .getQueueForGameRestrictions()
                        .contains(pace))
                .filter(c ->
                        tiglPredicate.test(toCsv(userSettingsByCandidate.get(c).getQueueForGameRestrictions())))
                .sorted(Comparator.comparing(
                                c -> parseHours(userSettingsByCandidate.get(c).getQueueForGameMaxQueueTime()))
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
            Map<String, Double> playerRatings,
            Double defaultRating) {
        UserSettings player1Settings = userSettingsByCandidate.get(player1);
        UserSettings player2Settings = userSettingsByCandidate.get(player2);
        String player1Id = player1.getUserId();
        String player2Id = player2.getUserId();
        if (player1Settings.getQueueForGameAvoidList().contains(player2Id)
                || player2Settings.getQueueForGameAvoidList().contains(player1Id)) {
            return false;
        }

        String player1RestrictionsCsv = toCsv(player1Settings.getQueueForGameRestrictions());
        String player2RestrictionsCsv = toCsv(player2Settings.getQueueForGameRestrictions());
        boolean doesPlayer1WantSimilarHours = MatchmakingOptions.wantsSimilarActiveHours(player1RestrictionsCsv);
        boolean doesPlayer2WantSimilarHours = MatchmakingOptions.wantsSimilarActiveHours(player2RestrictionsCsv);
        if (doesPlayer1WantSimilarHours || doesPlayer2WantSimilarHours) {
            Set<Integer> player1ActiveHourBuckets = playersToActiveHourBuckets.getOrDefault(player1, Set.of());
            Set<Integer> player2ActiveHourBuckets = playersToActiveHourBuckets.getOrDefault(player2, Set.of());
            long sharedBuckets = player1ActiveHourBuckets.stream()
                    .filter(player2ActiveHourBuckets::contains)
                    .count();
            if (sharedBuckets < ACTIVE_HOUR_SHARED_BUCKET_REQUIREMENT) return false;
        }

        boolean doesPlayer1WantSimilarSkill = MatchmakingOptions.wantsSimilarPlayerSkill(player1RestrictionsCsv);
        boolean doesPlayer2WantSimilarSkill = MatchmakingOptions.wantsSimilarPlayerSkill(player2RestrictionsCsv);
        if (doesPlayer1WantSimilarSkill || doesPlayer2WantSimilarSkill) {
            Double player1Rating =
                    Optional.of(playerRatings.get(player1.getUserId())).orElse(defaultRating);
            Double player2Rating =
                    Optional.of(playerRatings.get(player2.getUserId())).orElse(defaultRating);

            boolean relaxed = isHalfQueueTimePassed(player1, userSettingsByCandidate)
                    || isHalfQueueTimePassed(player2, userSettingsByCandidate);
            double tolerance =
                    relaxed ? RELAXED_SIMILAR_SKILL_DIFFERENCE_THRESHOLD : SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
            return Math.abs(player1Rating - player2Rating) <= tolerance;
        }

        return true;
    }

    private boolean isHalfQueueTimePassed(
            MatchmakingQueueEntryEntity player,
            Map<MatchmakingQueueEntryEntity, UserSettings> userSettingsByCandidate) {
        double maxHours = parseHours(userSettingsByCandidate.get(player).getQueueForGameMaxQueueTime());
        double hoursWaited =
                Duration.between(player.getQueuedAtUtc(), Instant.now()).toMinutes() / 60.0;
        return hoursWaited >= maxHours / SIMILAR_SKILL_DIFFERENCE_THRESHOLD;
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
            LocalDateTime now) {
        List<MatchmakingQueueEntryEntity> expired = entries.stream()
                .filter(entry -> entry.getQueuedAtUtc()
                        .plusHours(parseHours(userSettingsByEntry.get(entry).getQueueForGameMaxQueueTime()))
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
