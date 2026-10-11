package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// The leader scores about two points a round, so the rounds left are the leader's distance to the goal over two,
// rounded up, and never fewer than one: the round in which the game ends is still played.
class GameHorizonTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    @Test
    void goalDefaultsToTenPoints() {
        assertThat(GameHorizon.goal(test.game)).isEqualTo(10);

        test.game.setVp(14);

        assertThat(GameHorizon.goal(test.game)).isEqualTo(14);
    }

    @Test
    void roundsLeftFollowTheLeadersDistanceToTheGoal() {
        assertThat(GameHorizon.roundsLeft(test.game)).isEqualTo(5);

        test.points(test.sol, 4);
        assertThat(GameHorizon.roundsLeft(test.game)).isEqualTo(3);

        test.points(test.nekro, 7);
        assertThat(GameHorizon.roundsLeft(test.game)).isEqualTo(2);

        test.points(test.nekro, 2);
        assertThat(GameHorizon.roundsLeft(test.game)).isEqualTo(1);
    }

    @Test
    void atLeastOneRoundIsLeftOnceTheGoalIsReached() {
        test.points(test.nekro, 11);

        assertThat(GameHorizon.roundsLeft(test.game)).isEqualTo(1);
    }

    @Test
    void highestOtherPointsIgnoresTheSeatItself() {
        test.points(test.nekro, 6);
        test.points(test.sol, 3);

        assertThat(GameHorizon.highestOtherPoints(test.game, test.nekro)).isEqualTo(3);
        assertThat(GameHorizon.highestOtherPoints(test.game, test.sol)).isEqualTo(6);
        assertThat(GameHorizon.leaderPoints(test.game)).isEqualTo(6);
    }
}
