package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Holding Mecatol Rex lets its controller score the Imperial point in the rounds ahead: a quarter of a victory point
// for each of at most two later rounds, and nothing once the leader is a round from the goal.
class MecatolStakeTest extends BaseTi4Test {

    private AiTestGame test;
    private int scored;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(2);
    }

    private void leaderOn(int points) {
        scored++;
        test.game.scorePublicObjective(test.sol.getUserID(), test.game.addCustomPO("Leader points " + scored, points));
    }

    @Test
    void isWorthTwoImperialRoundsEarlyInTheGame() {
        leaderOn(2);

        assertThat(MecatolStake.holdValue(test.game, test.nekro))
                .isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE * 0.25 * 2)
                .isEqualTo(4.0);
    }

    @Test
    void shrinksAsTheLeaderApproachesTheGoal() {
        leaderOn(6);
        assertThat(MecatolStake.holdValue(test.game, test.nekro)).isEqualTo(2.0);

        leaderOn(2);
        assertThat(MecatolStake.holdValue(test.game, test.nekro)).isZero();
    }

    @Test
    void isNothingWhenTheLeaderIsOnePointFromTheGoal() {
        leaderOn(9);

        assertThat(MecatolStake.holdValue(test.game, test.nekro)).isZero();
    }

    @Test
    void assumesTenPointsWhenTheGameHasNoGoal() {
        test.game.setVp(0);
        leaderOn(2);

        assertThat(MecatolStake.holdValue(test.game, test.nekro)).isEqualTo(4.0);
    }

    @Test
    void readsNoHiddenInformation() {
        test.sol.setSecret("dhw");
        test.sol.setActionCard("sabo1");
        test.sol.setPromissoryNote("blue_ta", 17);
        Player spy = Mockito.spy(test.sol);
        test.game.getPlayers().put(spy.getUserID(), spy);

        MecatolStake.holdValue(test.game, test.nekro);
        MecatolStake.holdValue(test.game, spy);

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getSecrets();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }
}
