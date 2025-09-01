package ti4.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.assertj.core.util.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class TrueSkillMatchmakingRatingServiceTest {

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

        double winnerRating = sortedRatings.get(0).rating();
        double rank2Rating = sortedRatings.get(1).rating();
        assertThat(winnerRating).isGreaterThan(rank2Rating);

        double otherRank2Rating = sortedRatings.get(2).rating();
        assertThat(otherRank2Rating).isCloseTo(rank2Rating, within(0.02));

        double rank3Rating = sortedRatings.get(3).rating();
        assertThat(rank3Rating).isLessThan(rank2Rating);
    }

    @NotNull
    private static MatchmakingGame buildMatchmakingGame(String name, int[] ranks) {
        var players = new ArrayList<MatchmakingPlayer>();
        for (int i = 0; i < ranks.length; i++) {
            players.add(new MatchmakingPlayer("p" + i, "player" + i, ranks[i]));
        }
        return new MatchmakingGame(name, System.currentTimeMillis(), players);
    }
}
