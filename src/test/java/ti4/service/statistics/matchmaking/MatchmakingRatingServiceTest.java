package ti4.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Comparator;
import java.util.List;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

class MatchmakingRatingServiceTest {

    @Test
    void generatingRatingsTwiceGivesSameResult() {
        MatchmakingGame game = new MatchmakingGame(
                "game1",
                1L,
                Lists.newArrayList(
                        new MatchmakingPlayer("p1", "player1", 1),
                        new MatchmakingPlayer("p2", "player2", 2),
                        new MatchmakingPlayer("p3", "player3", 2),
                        new MatchmakingPlayer("p4", "player4", 3),
                        new MatchmakingPlayer("p5", "player5", 3),
                        new MatchmakingPlayer("p6", "player6", 3)));

        List<MatchmakingRating> ratings = MatchmakingRatingService.calculateRatings(Lists.newArrayList(game));
        List<MatchmakingRating> sortedRatings = ratings.stream()
                .sorted(Comparator.comparing(MatchmakingRating::rating)
                        .reversed()
                        .thenComparing(MatchmakingRating::userId))
                .toList();

        List<MatchmakingRating> ratings2 = MatchmakingRatingService.calculateRatings(Lists.newArrayList(game));
        List<MatchmakingRating> sortedRatings2 = ratings2.stream()
                .sorted(Comparator.comparing(MatchmakingRating::rating)
                        .reversed()
                        .thenComparing(MatchmakingRating::userId))
                .toList();

        assertThat(sortedRatings).isEqualTo(sortedRatings2);
    }

    @Test
    void generatesSensibleRatings() {
        MatchmakingGame game = new MatchmakingGame(
                "game1",
                1L,
                Lists.newArrayList(
                        new MatchmakingPlayer("p1", "player1", 1),
                        new MatchmakingPlayer("p2", "player2", 2),
                        new MatchmakingPlayer("p3", "player3", 2),
                        new MatchmakingPlayer("p4", "player4", 3),
                        new MatchmakingPlayer("p5", "player5", 3),
                        new MatchmakingPlayer("p6", "player6", 3)));

        List<MatchmakingRating> ratings = MatchmakingRatingService.calculateRatings(Lists.newArrayList(game));

        List<MatchmakingRating> sortedRatings = ratings.stream()
                .sorted(Comparator.comparing(MatchmakingRating::rating)
                        .reversed()
                        .thenComparing(MatchmakingRating::userId))
                .toList();

        assertThat(ratings).hasSize(6);

        double winnerRating = sortedRatings.get(0).rating();
        double rank2Rating = sortedRatings.get(1).rating();
        assertThat(winnerRating).isGreaterThan(rank2Rating);

        double otherRank2Rating = sortedRatings.get(2).rating();
        assertThat(otherRank2Rating).isCloseTo(rank2Rating, within(0.05));

        double rank3Rating = sortedRatings.get(3).rating();
        assertThat(rank3Rating).isLessThan(rank2Rating);
    }
}
