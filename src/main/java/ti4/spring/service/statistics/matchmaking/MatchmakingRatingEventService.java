package ti4.spring.service.statistics.matchmaking;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.gesundkrank.jskills.Rating;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Service;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class MatchmakingRatingEventService {

    private static final int MAX_LIST_SIZE = 50;
    private static final int INACTIVITY_MONTHS = 6;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final Duration RATINGS_CACHE_TTL = Duration.ofHours(8);
    private static final Duration AVERAGE_RATING_CACHE_TTL = Duration.ofHours(8);
    private static final String RATINGS_CACHE_KEY = "key";

    private final Cache<String, List<MatchmakingRating>> unconservativeRatingsCache = createRatingsCache();
    private final Cache<String, List<MatchmakingRating>> conservativeRatingsCache = createRatingsCache();
    private final Cache<String, BigDecimal> averageConservativeRatingCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(AVERAGE_RATING_CACHE_TTL)
            .build();

    private final PlayerEntityRepository playerEntityRepository;

    public void calculateRatings(SlashCommandInteractionEvent event) {
        boolean onlyTiglGames = event.getOption("tigl_only", Boolean.FALSE, OptionMapping::getAsBoolean);
        boolean showRating = event.getOption("show_my_rating", Boolean.FALSE, OptionMapping::getAsBoolean);

        List<PlayerEntity> players =
                playerEntityRepository.findAllWithUsersAndGamesByCompletedNonAllianceGame(onlyTiglGames);
        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players);
        List<MatchmakingRating> playerRatings = TrueSkillMatchmakingRatingService.calculateRatings(games, true);
        sendMessage(event, playerRatings, games, showRating);
    }

    public static long toDisplayRating(BigDecimal rating) {
        return rating.multiply(ONE_HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public Map<String, Rating> getPlayerRatings(Set<String> userIds) {
        return getCachedDefaultPlayerRatings().stream()
                .filter(mr -> userIds.contains(mr.userId()))
                .collect(Collectors.toMap(
                        MatchmakingRating::userId,
                        mr -> new Rating(mr.rating().doubleValue(), mr.sigma().doubleValue())));
    }

    public Map<String, BigDecimal> getConservativePlayerRatings(Set<String> userIds) {
        return filterRatingsByUserIds(getCachedConservativePlayerRatings(), userIds);
    }

    public Long getAverageDisplayRating(Collection<String> userIds) {
        if (userIds.isEmpty()) {
            return null;
        }
        return averageDisplayRating(userIds, getConservativePlayerRatings(new HashSet<>(userIds)));
    }

    public SkillTier getSkillTier(Collection<String> userIds) {
        if (userIds.isEmpty()) {
            return null;
        }
        Map<String, BigDecimal> ratings = getConservativePlayerRatings(new HashSet<>(userIds));
        long ratedCount = userIds.stream().filter(ratings::containsKey).count();
        if (!hasRatedQuorum(userIds.size(), ratedCount)) {
            return null;
        }
        return SkillTier.fromDisplayRating(averageDisplayRating(userIds, ratings));
    }

    private static boolean hasRatedQuorum(int playerCount, long ratedCount) {
        return ratedCount * 2 >= playerCount;
    }

    private long averageDisplayRating(Collection<String> userIds, Map<String, BigDecimal> ratings) {
        BigDecimal defaultRating = getAverageConservativeRating();
        BigDecimal average = userIds.stream()
                .map(userId -> ratings.getOrDefault(userId, defaultRating))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(userIds.size()), MathContext.DECIMAL64);
        return toDisplayRating(average);
    }

    public BigDecimal getAverageConservativeRating() {
        return averageConservativeRatingCache.get(RATINGS_CACHE_KEY, _ -> computeAverageConservativeRating());
    }

    private BigDecimal computeAverageConservativeRating() {
        List<MatchmakingRating> ratings = getCachedConservativePlayerRatings();
        return ratings.stream()
                .map(MatchmakingRating::rating)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(ratings.size()), java.math.MathContext.DECIMAL64);
    }

    private static Map<String, BigDecimal> filterRatingsByUserIds(
            List<MatchmakingRating> ratings, Set<String> userIds) {
        return ratings.stream()
                .filter(matchmakingRating -> userIds.contains(matchmakingRating.userId()))
                .collect(Collectors.toMap(MatchmakingRating::userId, MatchmakingRating::rating));
    }

    private List<MatchmakingRating> getCachedDefaultPlayerRatings() {
        return unconservativeRatingsCache.get(RATINGS_CACHE_KEY, _ -> getPlayerRatings(false, false));
    }

    private List<MatchmakingRating> getCachedConservativePlayerRatings() {
        return conservativeRatingsCache.get(RATINGS_CACHE_KEY, _ -> getPlayerRatings(false, true));
    }

    private static Cache<String, List<MatchmakingRating>> createRatingsCache() {
        return Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(RATINGS_CACHE_TTL)
                .recordStats()
                .build();
    }

    private List<MatchmakingRating> getPlayerRatings(boolean onlyTiglGames, boolean useConservativeRating) {
        List<PlayerEntity> players =
                playerEntityRepository.findAllWithUsersAndGamesByCompletedNonAllianceGame(onlyTiglGames);
        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players);
        return TrueSkillMatchmakingRatingService.calculateRatings(games, useConservativeRating);
    }

    private static void sendMessage(
            SlashCommandInteractionEvent event,
            List<MatchmakingRating> playerRatings,
            List<MatchmakingGame> games,
            boolean showRating) {
        int maxListSize = Math.min(MAX_LIST_SIZE, playerRatings.size());
        String ratingLabel = "Rating";
        long inactivityCutoff = Instant.now()
                .atZone(ZoneOffset.UTC)
                .minusMonths(INACTIVITY_MONTHS)
                .toInstant()
                .toEpochMilli();
        StringBuilder stringBuilder = new StringBuilder().append("__**Player Matchmaking Ratings:**__\n");
        for (int i = 0, listSize = 0; i < playerRatings.size() && listSize < maxListSize; i++) {
            var playerRating = playerRatings.get(i);
            if (playerRating.calibrationPercent().compareTo(ONE_HUNDRED) < 0) continue;
            if (playerRating.lastGameEndedDate() < inactivityCutoff) continue;

            listSize++;
            String formattedString = String.format(
                    "%d. `%s` `%s=%d`\n",
                    listSize, playerRating.username(), ratingLabel, toDisplayRating(playerRating.rating()));
            stringBuilder.append(formattedString);
        }

        BigDecimal averageRating = playerRatings.isEmpty()
                ? BigDecimal.ZERO
                : playerRatings.stream()
                        .map(MatchmakingRating::rating)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(playerRatings.size()), java.math.MathContext.DECIMAL64);
        String formattedString =
                String.format("""

                This list only includes the top %d players with a high confidence in their rating.

                The average %s of the player base is `%d`
                """, maxListSize, ratingLabel.toLowerCase(), toDisplayRating(averageRating));
        stringBuilder.append(formattedString);

        appendBracketDistribution(
                stringBuilder,
                "Players per " + ratingLabel.toLowerCase() + " bracket",
                "players",
                bucketPlayersByBracket(playerRatings));
        appendBracketDistribution(
                stringBuilder,
                "Games per average-" + ratingLabel.toLowerCase() + " bracket",
                "games",
                bucketGamesByBracket(games, playerRatings));

        playerRatings.stream()
                .filter(playerRating ->
                        playerRating.userId().equals(event.getUser().getId()))
                .findFirst()
                .ifPresent(playerRating -> {
                    if (showRating && playerRating.calibrationPercent().compareTo(ONE_HUNDRED) == 0) {
                        stringBuilder.append(String.format(
                                "\nYour %s is `%d`.",
                                ratingLabel.toLowerCase(), toDisplayRating(playerRating.rating())));
                    } else {
                        stringBuilder.append(String.format(
                                "\nWe are `%.1f%%` of the way to a high confidence in your rating.",
                                playerRating.calibrationPercent()));
                        if (showRating) {
                            stringBuilder.append(" We cannot show your rating until it reaches high confidence.");
                        }
                    }
                    if (showRating) {
                        appendAverageOpponentRating(
                                stringBuilder, ratingLabel, playerRating.userId(), games, playerRatings, averageRating);
                    }
                });

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(),
                "Player Matchmaking Ratings",
                stringBuilder.toString());
    }

    private static void appendAverageOpponentRating(
            StringBuilder stringBuilder,
            String ratingLabel,
            String userId,
            List<MatchmakingGame> games,
            List<MatchmakingRating> playerRatings,
            BigDecimal defaultRating) {
        Map<String, BigDecimal> ratingByUserId =
                playerRatings.stream().collect(Collectors.toMap(MatchmakingRating::userId, MatchmakingRating::rating));

        BigDecimal opponentRatingSum = BigDecimal.ZERO;
        int opponentCount = 0;
        int gameCount = 0;
        for (MatchmakingGame game : games) {
            if (game.players().stream().noneMatch(player -> player.userId().equals(userId))) continue;

            gameCount++;
            for (MatchmakingPlayer opponent : game.players()) {
                if (opponent.userId().equals(userId)) continue;
                opponentRatingSum =
                        opponentRatingSum.add(ratingByUserId.getOrDefault(opponent.userId(), defaultRating));
                opponentCount++;
            }
        }
        if (opponentCount == 0) return;

        BigDecimal averageOpponentRating =
                opponentRatingSum.divide(BigDecimal.valueOf(opponentCount), MathContext.DECIMAL64);
        stringBuilder.append(String.format(
                "\nThe average %s of your opponents across your %d games is `%d`.",
                ratingLabel.toLowerCase(), gameCount, toDisplayRating(averageOpponentRating)));
    }

    private static Map<Long, Long> bucketPlayersByBracket(List<MatchmakingRating> playerRatings) {
        Map<Long, Long> counts = new TreeMap<>(Comparator.reverseOrder());
        for (MatchmakingRating playerRating : playerRatings) {
            counts.merge(bracketFor(toDisplayRating(playerRating.rating())), 1L, Long::sum);
        }
        return counts;
    }

    private static Map<Long, Long> bucketGamesByBracket(
            List<MatchmakingGame> games, List<MatchmakingRating> playerRatings) {
        Map<String, BigDecimal> ratingByUserId =
                playerRatings.stream().collect(Collectors.toMap(MatchmakingRating::userId, MatchmakingRating::rating));
        Map<Long, Long> counts = new TreeMap<>(Comparator.reverseOrder());
        for (MatchmakingGame game : games) {
            BigDecimal sum = BigDecimal.ZERO;
            int ratedPlayers = 0;
            for (MatchmakingPlayer player : game.players()) {
                BigDecimal rating = ratingByUserId.get(player.userId());
                if (rating == null) continue;
                sum = sum.add(rating);
                ratedPlayers++;
            }
            if (ratedPlayers == 0) continue;
            BigDecimal averageRating = sum.divide(BigDecimal.valueOf(ratedPlayers), java.math.MathContext.DECIMAL64);
            counts.merge(bracketFor(toDisplayRating(averageRating)), 1L, Long::sum);
        }
        return counts;
    }

    private static long bracketFor(long displayRating) {
        return Math.floorDiv(displayRating, 100) * 100;
    }

    private static void appendBracketDistribution(
            StringBuilder stringBuilder, String heading, String unitLabel, Map<Long, Long> countsByBracket) {
        if (countsByBracket.isEmpty()) return;
        stringBuilder.append("\n**").append(heading).append(":**\n");
        for (Map.Entry<Long, Long> entry : countsByBracket.entrySet()) {
            long bracket = entry.getKey();
            long count = entry.getValue();
            String separator = bracket < 0 ? " to " : "-";
            String label = count == 1 ? unitLabel.substring(0, unitLabel.length() - 1) : unitLabel;
            stringBuilder.append(String.format("- `%d%s%d`: %d %s\n", bracket, separator, bracket + 99, count, label));
        }
    }

    public static MatchmakingRatingEventService get() {
        return SpringContext.getBean(MatchmakingRatingEventService.class);
    }
}
