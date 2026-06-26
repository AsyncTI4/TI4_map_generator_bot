package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class MatchmakingRatingServiceTest {

    // Ratings only count players with at least three completed games, so fixtures repeat the same game thrice.
    private static final int GAMES_TO_QUALIFY = 3;
    private static final int[] RANKS = {1, 2, 2, 3, 3, 3};

    @Test
    void generatingRatingsTwiceGivesSameResult() {
        List<MatchmakingRating> sortedRatings = sortedByRating(
                TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(GAMES_TO_QUALIFY, RANKS)));
        List<MatchmakingRating> sortedRatings2 = sortedByRating(
                TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(GAMES_TO_QUALIFY, RANKS)));

        assertThat(sortedRatings).isEqualTo(sortedRatings2);
    }

    @Test
    void generatesSensibleRatings() {
        List<MatchmakingRating> ratings =
                TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(GAMES_TO_QUALIFY, RANKS));

        List<MatchmakingRating> sortedRatings = sortedByRating(ratings);

        assertThat(ratings).hasSize(6);

        BigDecimal winnerRating = sortedRatings.get(0).rating();
        BigDecimal rank2Rating = sortedRatings.get(1).rating();
        assertThat(winnerRating).isGreaterThan(rank2Rating);

        BigDecimal otherRank2Rating = sortedRatings.get(2).rating();
        assertThat(otherRank2Rating.subtract(rank2Rating).abs()).isLessThan(BigDecimal.valueOf(0.02));

        BigDecimal rank3Rating = sortedRatings.get(3).rating();
        assertThat(rank3Rating).isLessThan(rank2Rating);
    }

    @Test
    void excludesPlayersWithFewerThanThreeCompletedGames() {
        // Two games each means every player has only two completed games, below the three-game minimum.
        assertThat(TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(2, RANKS)))
                .isEmpty();
        assertThat(TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(3, RANKS)))
                .hasSize(6);
    }

    @Test
    void conservativeRatingsUseConservativeTrueSkillValue() {
        List<MatchmakingRating> meanRatings =
                TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(GAMES_TO_QUALIFY, RANKS));
        List<MatchmakingRating> conservativeRatings =
                TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(GAMES_TO_QUALIFY, RANKS), true);

        MatchmakingRating meanWinnerRating = findRatingForUser(meanRatings, "p0");
        MatchmakingRating conservativeWinnerRating = findRatingForUser(conservativeRatings, "p0");

        assertThat(conservativeWinnerRating.rating()).isLessThan(meanWinnerRating.rating());
        assertThat(conservativeWinnerRating.calibrationPercent()).isEqualTo(meanWinnerRating.calibrationPercent());
    }

    private static List<MatchmakingRating> sortedByRating(List<MatchmakingRating> ratings) {
        return ratings.stream()
                .sorted(Comparator.comparing(MatchmakingRating::rating)
                        .reversed()
                        .thenComparing(MatchmakingRating::userId))
                .toList();
    }

    @NotNull
    private static List<MatchmakingGame> buildRankedGames(int gameCount, int[] ranks) {
        List<MatchmakingGame> games = new ArrayList<>();
        for (int i = 0; i < gameCount; i++) {
            games.add(buildMatchmakingGame("game" + i, ranks));
        }
        return games;
    }

    @NotNull
    private static MatchmakingGame buildMatchmakingGame(String name, int[] ranks) {
        var players = new ArrayList<MatchmakingPlayer>();
        for (int i = 0; i < ranks.length; i++) {
            players.add(new MatchmakingPlayer("p" + i, "player" + i, ranks[i]));
        }
        return new MatchmakingGame(name, System.currentTimeMillis(), players);
    }

    private static MatchmakingRating findRatingForUser(List<MatchmakingRating> ratings, String userId) {
        return ratings.stream()
                .filter(rating -> rating.userId().equals(userId))
                .findFirst()
                .orElseThrow();
    }
}
