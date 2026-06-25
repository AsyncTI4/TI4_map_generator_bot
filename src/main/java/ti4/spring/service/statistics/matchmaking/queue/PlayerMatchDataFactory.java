package ti4.spring.service.statistics.matchmaking.queue;

import de.gesundkrank.jskills.Rating;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedPlayer;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.service.statistics.UserGameInfoService;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingEventService;

@UtilityClass
class PlayerMatchDataFactory {

    private static final int NUMBER_OF_ACTIVE_HOUR_BUCKETS = 6;
    private static final int ACTIVE_HOUR_BUCKET_SIZE = 4;
    private static final int ACTIVE_HOUR_BUCKET_MATCH_THRESHOLD = 3;
    private static final Rating DEFAULT_NEW_PLAYER_RATING = new Rating(25.0, 8.333);

    static Map<MatchmakingQueueMember, PlayerMatchmakingData> buildForParties(List<QueuedParty> parties) {
        Set<String> userIds = parties.stream()
                .flatMap(party -> party.members().stream())
                .map(MatchmakingQueueMember::getUserId)
                .collect(Collectors.toSet());
        Map<String, Rating> ratings = MatchmakingRatingEventService.get().getPlayerRatings(userIds);
        Guild guild = JdaService.guildPrimary;
        Instant now = Instant.now();

        Map<MatchmakingQueueMember, PlayerMatchmakingData> matchData = new HashMap<>();
        for (QueuedParty party : parties) {
            List<String> leaderRestrictions = party.leaderSettings().getMatchmakingRestrictions();
            boolean relaxConstraints = shouldRelaxConstraints(party, now);
            for (MatchmakingQueueMember member : party.members()) {
                matchData.put(member, build(member.getUserId(), leaderRestrictions, ratings, guild, relaxConstraints));
            }
        }
        return matchData;
    }

    private static boolean shouldRelaxConstraints(QueuedParty party, Instant now) {
        double hoursWaited = Duration.between(party.party().getQueuedAt(), now).toMinutes() / 60.0;
        return hoursWaited >= 4;
    }

    static Map<String, PlayerMatchmakingData> buildForUsers(List<String> userIds, List<String> leaderRestrictions) {
        Map<String, Rating> ratings = MatchmakingRatingEventService.get().getPlayerRatings(new HashSet<>(userIds));
        Guild guild = JdaService.guildPrimary;

        Map<String, PlayerMatchmakingData> dataById = new HashMap<>();
        for (String id : userIds) {
            dataById.put(id, build(id, leaderRestrictions, ratings, guild, false));
        }
        return dataById;
    }

    private static PlayerMatchmakingData build(
            String userId,
            List<String> leaderRestrictions,
            Map<String, Rating> ratings,
            Guild guild,
            boolean halfQueueTimePassed) {
        UserSettings ownSettings = UserSettingsManager.get(userId);
        return new PlayerMatchmakingData(
                userId,
                leaderRestrictions,
                ownSettings.getMatchmakingAvoidList(),
                ratings.getOrDefault(userId, DEFAULT_NEW_PLAYER_RATING),
                computeActiveHourBuckets(ownSettings.getActiveHoursAsIntegers()),
                completedGames(userId),
                roleNames(guild, userId),
                halfQueueTimePassed);
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

    private static Set<Integer> computeActiveHourBuckets(Set<Integer> activeHours) {
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

    private static int getBucketScore(Set<Integer> activeHours, int startInclusive, int endInclusive) {
        int score = 0;
        for (int hour : activeHours) {
            if (hour >= startInclusive && hour <= endInclusive) {
                score++;
            }
        }
        return score;
    }
}
