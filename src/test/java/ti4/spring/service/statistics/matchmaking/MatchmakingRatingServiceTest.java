package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.assertj.core.util.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class MatchmakingRatingServiceTest {

    @Test
    void generatingRatingsTwiceGivesSameResult() {
        MatchmakingGame game = buildMatchmakingGame("game1", new int[] {1, 2, 2, 3, 3, 3});

        List<MatchmakingRating> ratings = TrueSkillMatchmakingRatingService.calculateRatings(Lists.newArrayList(game));
        List<MatchmakingRating> sortedRatings = ratings.stream()
                .sorted(Comparator.comparing(MatchmakingRating::rating)
                        .reversed()
                        .thenComparing(MatchmakingRating::userId))
                .toList();

        List<MatchmakingRating> ratings2 = TrueSkillMatchmakingRatingService.calculateRatings(Lists.newArrayList(game));
        List<MatchmakingRating> sortedRatings2 = ratings2.stream()
                .sorted(Comparator.comparing(MatchmakingRating::rating)
                        .reversed()
                        .thenComparing(MatchmakingRating::userId))
                .toList();

        assertThat(sortedRatings).isEqualTo(sortedRatings2);
    }

    @Test
    void generatesSensibleRatings() {
        MatchmakingGame game = buildMatchmakingGame("game1", new int[] {1, 2, 2, 3, 3, 3});

        List<MatchmakingRating> ratings = TrueSkillMatchmakingRatingService.calculateRatings(Lists.newArrayList(game));

        List<MatchmakingRating> sortedRatings = ratings.stream()
                .sorted(Comparator.comparing(MatchmakingRating::rating)
                        .reversed()
                        .thenComparing(MatchmakingRating::userId))
                .toList();

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
    void conservativeRatingsUseConservativeTrueSkillValue() {
        MatchmakingGame game = buildMatchmakingGame("game1", new int[] {1, 2, 2, 3, 3, 3});

        List<MatchmakingRating> meanRatings =
                TrueSkillMatchmakingRatingService.calculateRatings(Lists.newArrayList(game));
        List<MatchmakingRating> conservativeRatings =
                TrueSkillMatchmakingRatingService.calculateRatings(Lists.newArrayList(game), true);

        MatchmakingRating meanWinnerRating = findRatingForUser(meanRatings, "p0");
        MatchmakingRating conservativeWinnerRating = findRatingForUser(conservativeRatings, "p0");

        assertThat(conservativeWinnerRating.rating()).isLessThan(meanWinnerRating.rating());
        assertThat(conservativeWinnerRating.calibrationPercent()).isEqualTo(meanWinnerRating.calibrationPercent());
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
