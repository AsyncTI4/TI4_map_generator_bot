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

    // Human tables usually finish by the status phase of round 5 in a ten point game, so the last round is taken to
    // be half the goal, rounded up.
    @Test
    void theLastRoundIsHalfTheGoalRoundedUp() {
        assertThat(GameHorizon.lateRound(test.game)).isEqualTo(5);

        test.game.setVp(14);
        assertThat(GameHorizon.lateRound(test.game)).isEqualTo(7);

        test.game.setVp(11);
        assertThat(GameHorizon.lateRound(test.game)).isEqualTo(6);
    }

    // The end is in sight from the last round on, or as soon as anyone, the seat or an opponent, is within four of the
    // goal: 6 of 10.
    @Test
    void theEndIsInSightFromTheLastRoundOrWhenAnyoneIsWithinFourOfTheGoal() {
        test.game.setRound(4);
        test.points(test.sol, 5);
        assertThat(GameHorizon.endInSight(test.game)).isFalse();

        test.points(test.nekro, 6);
        assertThat(GameHorizon.endInSight(test.game)).isTrue();

        AiTestGame fresh = new AiTestGame();
        fresh.game.setRound(5);
        assertThat(GameHorizon.endInSight(fresh.game)).isTrue();
        fresh.game.setRound(4);
        assertThat(GameHorizon.endInSight(fresh.game)).isFalse();
        fresh.points(fresh.sol, 6);
        assertThat(GameHorizon.endInSight(fresh.game)).isTrue();
    }

    @Test
    void theEndInSightScalesWithTheGoal() {
        test.game.setVp(14);
        test.game.setRound(6);
        test.points(test.sol, 9);
        assertThat(GameHorizon.endInSight(test.game)).isFalse();

        test.points(test.sol, 1);
        assertThat(GameHorizon.endInSight(test.game)).isTrue();

        AiTestGame fresh = new AiTestGame();
        fresh.game.setVp(14);
        fresh.game.setRound(6);
        assertThat(GameHorizon.endInSight(fresh.game)).isFalse();
        fresh.game.setRound(7);
        assertThat(GameHorizon.endInSight(fresh.game)).isTrue();
    }

    // The rounds after this one before the likely last round: the fewer of the rounds until the last round and the
    // rounds the leader needs, at two a round, to get within four of the goal.
    @Test
    void roundsBeforeTheEndAreTheFewerOfTheRoundsAndThePointsToGo() {
        test.game.setRound(1);
        assertThat(GameHorizon.roundsBeforeEnd(test.game)).isEqualTo(3);

        test.game.setRound(3);
        test.points(test.sol, 4);
        assertThat(GameHorizon.roundsBeforeEnd(test.game)).isEqualTo(1);

        test.game.setRound(4);
        test.points(test.sol, 1);
        assertThat(GameHorizon.roundsBeforeEnd(test.game)).isEqualTo(1);

        test.points(test.sol, 1);
        assertThat(GameHorizon.roundsBeforeEnd(test.game)).isZero();

        test.game.setRound(7);
        assertThat(GameHorizon.roundsBeforeEnd(test.game)).isZero();
    }
}
