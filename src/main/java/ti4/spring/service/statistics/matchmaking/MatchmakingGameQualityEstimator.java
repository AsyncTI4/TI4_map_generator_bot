package ti4.spring.service.statistics.matchmaking;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class MatchmakingGameQualityEstimator {

    private static final double HIGH_CONFIDENCE_CALIBRATION_PERCENT = 100;
    private static final double LOW_SKILL_Z_SCORE_THRESHOLD = -0.5;
    private static final double HIGH_SKILL_Z_SCORE_THRESHOLD = 0.5;
    private static final double LOW_DIFFERENCE_STDDEV_THRESHOLD = 1.0;
    private static final double HIGH_DIFFERENCE_STDDEV_THRESHOLD = 2.0;

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public MatchmakingGameQuality estimate(List<String> lobbyUserIds) {
        if (lobbyUserIds.isEmpty()) {
            return MatchmakingGameQuality.unknown();
        }

        List<PlayerEntity> players =
                playerEntityRepository.findAllWithUsersAndGamesByCompletedSixPlayerNonAllianceGame(false);
        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players);
        List<MatchmakingRating> ratings = TrueSkillMatchmakingRatingService.calculateRatings(games);
        return estimate(ratings, lobbyUserIds);
    }

    @Transactional(readOnly = true)
    public String buildLobbyRatingLogMessage(List<String> lobbyUserIds) {
        if (lobbyUserIds.isEmpty()) {
            return buildLobbyRatingLogMessage(List.of(), List.of());
        }

        List<PlayerEntity> players =
                playerEntityRepository.findAllWithUsersAndGamesByCompletedSixPlayerNonAllianceGame(false);
        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players);
        List<MatchmakingRating> ratings = TrueSkillMatchmakingRatingService.calculateRatings(games);
        return buildLobbyRatingLogMessage(ratings, lobbyUserIds);
    }

    static MatchmakingGameQuality estimate(List<MatchmakingRating> ratings, List<String> lobbyUserIds) {
        if (lobbyUserIds.isEmpty()) {
            return MatchmakingGameQuality.unknown();
        }

        List<MatchmakingRating> calibratedRatings = ratings.stream()
                .filter(rating -> rating.calibrationPercent() >= HIGH_CONFIDENCE_CALIBRATION_PERCENT)
                .sorted(Comparator.comparing(MatchmakingRating::userId))
                .toList();
        if (calibratedRatings.isEmpty()) {
            return MatchmakingGameQuality.unknown();
        }

        Map<String, Double> calibratedRatingsByUserId = calibratedRatings.stream()
                .collect(Collectors.toMap(
                        MatchmakingRating::userId, MatchmakingRating::rating, (existing, replacement) -> existing));
        List<Double> lobbyRatings = lobbyUserIds.stream()
                .map(calibratedRatingsByUserId::get)
                .filter(rating -> rating != null)
                .toList();

        double populationAverage = average(calibratedRatings, MatchmakingRating::rating);
        double populationStandardDeviation = standardDeviation(calibratedRatings, MatchmakingRating::rating);
        MatchmakingSkillLevel skillRating = hasEnoughKnownRatings(lobbyRatings.size(), lobbyUserIds.size(), 1)
                ? classifySkillRating(lobbyRatings, populationAverage, populationStandardDeviation)
                : MatchmakingSkillLevel.UNKNOWN;
        MatchmakingSkillLevel skillDifference = hasEnoughKnownRatings(lobbyRatings.size(), lobbyUserIds.size(), 2)
                ? classifySkillDifference(lobbyRatings, populationStandardDeviation)
                : MatchmakingSkillLevel.UNKNOWN;

        return new MatchmakingGameQuality(skillRating, skillDifference);
    }

    static String buildLobbyRatingLogMessage(List<MatchmakingRating> ratings, List<String> lobbyUserIds) {
        MatchmakingGameQuality quality = estimate(ratings, lobbyUserIds);
        Map<String, MatchmakingRating> ratingsByUserId = ratings.stream()
                .collect(Collectors.toMap(
                        MatchmakingRating::userId, rating -> rating, (existing, replacement) -> existing));
        String playerRatings = lobbyUserIds.stream()
                .map(userId -> formatPlayerRating(userId, ratingsByUserId.get(userId)))
                .collect(Collectors.joining("\n"));
        return """
                Lobby matchmaking summary:
                > Lobby Skill Rating: `%s`
                > Skill Rating Difference: `%s`
                > Player Ratings:
                %s
                """.formatted(quality.skillRating(), quality.skillDifference(), playerRatings);
    }

    private static String formatPlayerRating(String userId, MatchmakingRating rating) {
        if (rating == null) {
            return "- `%s`: `UNKNOWN`".formatted(userId);
        }
        return "- `%s` (`%s`): `%.3f` (`%.1f%%` calibrated)"
                .formatted(rating.username(), userId, rating.rating(), rating.calibrationPercent());
    }

    private static boolean hasEnoughKnownRatings(int knownRatingCount, int lobbySize, int minimumKnownRatings) {
        int requiredKnownRatings = Math.max(minimumKnownRatings, lobbySize / 2 + 1);
        return knownRatingCount >= requiredKnownRatings;
    }

    private static MatchmakingSkillLevel classifySkillRating(
            List<Double> lobbyRatings, double populationAverage, double populationStandardDeviation) {
        double averageRating = average(lobbyRatings);
        double zScore = populationStandardDeviation == 0
                ? 0
                : (averageRating - populationAverage) / populationStandardDeviation;

        if (zScore <= LOW_SKILL_Z_SCORE_THRESHOLD) {
            return MatchmakingSkillLevel.LOW;
        }
        if (zScore >= HIGH_SKILL_Z_SCORE_THRESHOLD) {
            return MatchmakingSkillLevel.HIGH;
        }
        return MatchmakingSkillLevel.MEDIUM;
    }

    private static MatchmakingSkillLevel classifySkillDifference(
            List<Double> lobbyRatings, double populationStandardDeviation) {
        double maxRating =
                lobbyRatings.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double minRating =
                lobbyRatings.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double normalizedDifference =
                populationStandardDeviation == 0 ? 0 : (maxRating - minRating) / populationStandardDeviation;

        if (normalizedDifference <= LOW_DIFFERENCE_STDDEV_THRESHOLD) {
            return MatchmakingSkillLevel.LOW;
        }
        if (normalizedDifference >= HIGH_DIFFERENCE_STDDEV_THRESHOLD) {
            return MatchmakingSkillLevel.HIGH;
        }
        return MatchmakingSkillLevel.MEDIUM;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private static <T> double average(List<T> values, ToDoubleFunction<T> mapper) {
        return values.stream().mapToDouble(mapper).average().orElse(Double.NaN);
    }

    private static <T> double standardDeviation(List<T> values, ToDoubleFunction<T> mapper) {
        double average = average(values, mapper);
        return Math.sqrt(values.stream()
                .mapToDouble(value -> {
                    double delta = mapper.applyAsDouble(value) - average;
                    return delta * delta;
                })
                .average()
                .orElse(0));
    }

    public static MatchmakingGameQualityEstimator getBean() {
        return SpringContext.getBean(MatchmakingGameQualityEstimator.class);
    }
}
