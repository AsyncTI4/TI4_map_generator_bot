package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.Rating;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchQualityCalculatorTest {

    private static final Rating NEW_PLAYER_RATING =
            GameInfo.getDefaultGameInfo().getDefaultRating();
    // Confident sigma, as for calibrated players.
    private static final double CONFIDENT_SIGMA = 1.5;

    @Test
    void balancedAllNewPlayersScoreNearOne() {
        // The motivating case: six brand-new players are perfectly balanced, yet raw quality is tiny
        // because TrueSkill penalizes their uncertainty.
        Fixture fixture = fixture(
                NEW_PLAYER_RATING,
                NEW_PLAYER_RATING,
                NEW_PLAYER_RATING,
                NEW_PLAYER_RATING,
                NEW_PLAYER_RATING,
                NEW_PLAYER_RATING);

        MatchQualityCalculator.Result result = MatchQualityCalculator.calculate(fixture.members(), fixture.data());

        assertThat(result.normalized()).isCloseTo(1.0, within(1e-6));
        assertThat(result.raw()).isLessThan(0.2);
    }

    @Test
    void balancedCalibratedPlayersScoreNearOne() {
        Fixture fixture = fixture(
                new Rating(30, CONFIDENT_SIGMA),
                new Rating(30, CONFIDENT_SIGMA),
                new Rating(30, CONFIDENT_SIGMA),
                new Rating(30, CONFIDENT_SIGMA));

        MatchQualityCalculator.Result result = MatchQualityCalculator.calculate(fixture.members(), fixture.data());

        assertThat(result.normalized()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void equalMeansMixedSigmasScoreNearOne() {
        Fixture fixture = fixture(new Rating(25, 25.0 / 3), new Rating(25, 4.0), new Rating(25, CONFIDENT_SIGMA));

        MatchQualityCalculator.Result result = MatchQualityCalculator.calculate(fixture.members(), fixture.data());

        assertThat(result.normalized()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void lopsidedGroupScoresLow() {
        Fixture fixture = fixture(
                new Rating(20, CONFIDENT_SIGMA), new Rating(25, CONFIDENT_SIGMA), new Rating(40, CONFIDENT_SIGMA));

        MatchQualityCalculator.Result result = MatchQualityCalculator.calculate(fixture.members(), fixture.data());

        assertThat(result.normalized()).isLessThan(0.3);
    }

    @Test
    void normalizedQualityIsInvariantAcrossPlayerCounts() {
        Rating rating = new Rating(30, CONFIDENT_SIGMA);
        Fixture threePlayers = fixture(rating, rating, rating);
        Fixture sixPlayers = fixture(rating, rating, rating, rating, rating, rating);

        MatchQualityCalculator.Result threePlayerResult =
                MatchQualityCalculator.calculate(threePlayers.members(), threePlayers.data());
        MatchQualityCalculator.Result sixPlayerResult =
                MatchQualityCalculator.calculate(sixPlayers.members(), sixPlayers.data());

        assertThat(threePlayerResult.normalized()).isCloseTo(1.0, within(1e-6));
        assertThat(sixPlayerResult.normalized()).isCloseTo(1.0, within(1e-6));
        // Raw quality degrades with player count even for identical players; normalized does not.
        assertThat(sixPlayerResult.raw()).isLessThan(threePlayerResult.raw());
    }

    private static Fixture fixture(Rating... ratings) {
        List<MatchmakingQueueMember> members = new ArrayList<>();
        Map<MatchmakingQueueMember, PlayerMatchmakingData> data = new HashMap<>();
        for (int i = 0; i < ratings.length; i++) {
            String userId = "user" + i;
            MatchmakingQueueMember member = new MatchmakingQueueMember();
            member.setUserId(userId);
            members.add(member);
            data.put(
                    member,
                    new PlayerMatchmakingData(
                            userId,
                            List.of(),
                            List.of(),
                            ratings[i],
                            Set.of(),
                            0,
                            Set.of(),
                            Duration.ZERO,
                            false,
                            List.of()));
        }
        return new Fixture(members, data);
    }

    private record Fixture(
            List<MatchmakingQueueMember> members, Map<MatchmakingQueueMember, PlayerMatchmakingData> data) {}
}
