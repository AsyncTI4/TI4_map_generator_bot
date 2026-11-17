package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// A seat is desperate for a point it must not miss: a public objective every status phase, a status secret from
// round 3, and every action secret from round 5 or once a rival can win this round.
class DesperationTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    @Test
    void isAlwaysDesperateForAPublicWhenNoneIsReady() {
        test.game.setRound(1);

        assertThat(Desperation.forPublic(test.nekro, false)).isTrue();
        assertThat(Desperation.forPublic(test.nekro, true)).isFalse();
    }

    @Test
    void setsUpAStatusSecretFromRoundThree() {
        test.game.setRound(2);
        assertThat(Desperation.forStatusSecretSetup(test.game, test.nekro, false))
                .isFalse();

        test.game.setRound(3);
        assertThat(Desperation.forStatusSecretSetup(test.game, test.nekro, false))
                .isTrue();
        assertThat(Desperation.forStatusSecretSetup(test.game, test.nekro, true))
                .isFalse();
    }

    @Test
    void chasesEveryActionSecretFromRoundFive() {
        test.game.setRound(4);
        assertThat(Desperation.actionSecretFactor(test.game, test.nekro)).isEqualTo(1.0);

        test.game.setRound(5);
        assertThat(Desperation.actionSecretFactor(test.game, test.nekro)).isEqualTo(Desperation.FACTOR);
    }

    @Test
    void chasesEveryActionSecretEarlyWhenARivalCanWinThisRound() {
        // Imperial on 9 points is a win this round when the seat does not weigh whether Mecatol Rex is reachable.
        AiExperiments.disable(test.nekro, Set.of(Experiment.WIN_THREAT_FEASIBLE));
        test.game.setRound(3);
        for (int card = 1; card <= 8; card++) test.game.setScTradeGood(card, 0);
        test.sol.addSC(8);
        test.points(test.sol, 9);
        assertThat(WinRace.canWinThisRound(test.game, test.nekro, test.sol)).isTrue();

        assertThat(Desperation.forActionSecrets(test.game, test.nekro)).isTrue();
    }

    @Test
    void isNeverDesperateWithTheExperimentOff() {
        test.game.setRound(6);
        AiExperiments.disable(test.nekro, Set.of(Experiment.DESPERATION));

        assertThat(Desperation.forActionSecrets(test.game, test.nekro)).isFalse();
        assertThat(Desperation.forPublic(test.nekro, false)).isFalse();
    }
}
