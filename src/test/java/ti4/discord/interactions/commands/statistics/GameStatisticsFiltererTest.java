package ti4.discord.interactions.commands.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.spring.service.statistics.matchmaking.MatchmakingMedal;
import ti4.spring.service.statistics.matchmaking.MatchmakingRating;

class GameStatisticsFiltererTest {

    @Test
    void calculatesPercentileForAverageRating() {
        List<MatchmakingRating> ratings =
                List.of(rating("p0", 10), rating("p1", 20), rating("p2", 30), rating("p3", 40));

        var percentile = GameStatisticsFilterer.calculateAverageRatingPercentile(ratings, Set.of("p1", "p2"));

        assertThat(percentile).contains(BigDecimal.valueOf(50));
    }

    @Test
    void averageRatingPercentileIsEmptyWhenNoPlayersMatch() {
        List<MatchmakingRating> ratings = List.of(rating("p0", 10));

        var percentile = GameStatisticsFilterer.calculateAverageRatingPercentile(ratings, Set.of("missing"));

        assertThat(percentile).isEmpty();
    }

    @Test
    void averageRatingPercentileIsEmptyWhenRatingBaseIsEmpty() {
        var percentile = GameStatisticsFilterer.calculateAverageRatingPercentile(List.of(), Set.of("p0"));

        assertThat(percentile).isEmpty();
    }

    @Test
    void getsMatchmakingMedalFromPercentile() {
        assertThat(MatchmakingMedal.fromPercentile(BigDecimal.valueOf(19.99))).isEqualTo(MatchmakingMedal.AGENT);
        assertThat(MatchmakingMedal.fromPercentile(BigDecimal.valueOf(20))).isEqualTo(MatchmakingMedal.COUNCILOR);
        assertThat(MatchmakingMedal.fromPercentile(BigDecimal.valueOf(20.01))).isEqualTo(MatchmakingMedal.COUNCILOR);
        assertThat(MatchmakingMedal.fromPercentile(BigDecimal.valueOf(40))).isEqualTo(MatchmakingMedal.COUNCILOR);
        assertThat(MatchmakingMedal.fromPercentile(BigDecimal.valueOf(60))).isEqualTo(MatchmakingMedal.COMMANDER);
        assertThat(MatchmakingMedal.fromPercentile(BigDecimal.valueOf(80))).isEqualTo(MatchmakingMedal.CUSTODIAN);
        assertThat(MatchmakingMedal.fromPercentile(BigDecimal.valueOf(100))).isEqualTo(MatchmakingMedal.HERO);
    }

    private static MatchmakingRating rating(String userId, int rating) {
        return new MatchmakingRating(userId, "player" + userId, BigDecimal.valueOf(rating), BigDecimal.valueOf(100));
    }
}
