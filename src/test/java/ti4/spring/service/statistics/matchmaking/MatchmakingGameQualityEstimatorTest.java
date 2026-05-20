package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class MatchmakingGameQualityEstimatorTest {

    @Test
    void estimatesLowLobbySkillAndLowDifference() {
        List<MatchmakingRating> ratings = buildRatings();

        MatchmakingGameQuality quality = MatchmakingGameQualityEstimator.estimate(ratings, List.of("p1", "p2"));

        assertThat(quality.skillRating()).isEqualTo(MatchmakingSkillLevel.LOW);
        assertThat(quality.skillDifference()).isEqualTo(MatchmakingSkillLevel.LOW);
    }

    @Test
    void estimatesMediumLobbySkillAndDifference() {
        List<MatchmakingRating> ratings = buildRatings();

        MatchmakingGameQuality quality = MatchmakingGameQualityEstimator.estimate(ratings, List.of("p2", "p4"));

        assertThat(quality.skillRating()).isEqualTo(MatchmakingSkillLevel.MEDIUM);
        assertThat(quality.skillDifference()).isEqualTo(MatchmakingSkillLevel.MEDIUM);
    }

    @Test
    void estimatesHighLobbySkillAndLowDifference() {
        List<MatchmakingRating> ratings = buildRatings();

        MatchmakingGameQuality quality = MatchmakingGameQualityEstimator.estimate(ratings, List.of("p4", "p5"));

        assertThat(quality.skillRating()).isEqualTo(MatchmakingSkillLevel.HIGH);
        assertThat(quality.skillDifference()).isEqualTo(MatchmakingSkillLevel.LOW);
    }

    @Test
    void returnsUnknownWhenMostLobbyPlayersDoNotHaveHighConfidenceRatings() {
        List<MatchmakingRating> ratings = buildRatings();

        MatchmakingGameQuality quality = MatchmakingGameQualityEstimator.estimate(ratings, List.of("p4", "p6", "p7"));

        assertThat(quality.skillRating()).isEqualTo(MatchmakingSkillLevel.UNKNOWN);
        assertThat(quality.skillDifference()).isEqualTo(MatchmakingSkillLevel.UNKNOWN);
    }

    @NotNull
    private static List<MatchmakingRating> buildRatings() {
        return List.of(
                new MatchmakingRating("p1", "player1", 15, 100),
                new MatchmakingRating("p2", "player2", 20, 100),
                new MatchmakingRating("p3", "player3", 25, 100),
                new MatchmakingRating("p4", "player4", 30, 100),
                new MatchmakingRating("p5", "player5", 35, 100),
                new MatchmakingRating("p6", "player6", 40, 50),
                new MatchmakingRating("p7", "player7", 10, 50));
    }
}
