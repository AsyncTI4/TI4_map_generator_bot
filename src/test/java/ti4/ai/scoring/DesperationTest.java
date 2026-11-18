package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.secrets.ActionSecretTiming;
import ti4.testUtils.BaseTi4Test;

// A seat is desperate for a point it must not miss: a public objective from round 2, a status secret from round 3,
// and every action secret from round 5 or once a rival can win this round. Desperation weighs a point 1.2x, or 1.5x
// when a rival is 2+ points ahead or can win this round. Public and status secret desperation stop once the seat
// is in position to score that kind this status phase. All three are off by default; these cases switch them on.
class DesperationTest extends BaseTi4Test {

    private static final Set<Experiment> ALL_KINDS = EnumSet.of(
            Experiment.DESPERATION_PUBLIC, Experiment.DESPERATION_STATUS_SECRET, Experiment.DESPERATION_ACTION_SECRET);

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        AiExperiments.enable(test.nekro, ALL_KINDS);
    }

    @Test
    void isOffByDefault() {
        test.game.setRound(6);

        assertThat(Desperation.forPublic(test.game, test.sol, false)).isFalse();
        assertThat(Desperation.forStatusSecretSetup(test.game, test.sol, false)).isFalse();
        assertThat(Desperation.forActionSecrets(test.game, test.sol)).isFalse();
    }

    @Test
    void isDesperateForAPublicFromRoundTwoWhenNoneIsReady() {
        test.game.setRound(1);
        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isFalse();

        test.game.setRound(2);
        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isTrue();
        assertThat(Desperation.forPublic(test.game, test.nekro, true)).isFalse();
    }

    // After round 1's status phase, the next public the seat can score is round 2's.
    @Test
    void looksAheadToNextRoundsScoringOnceThisRoundsIsOver() {
        test.game.setRound(1);
        test.game.setPhaseOfGame("statusHomework");

        assertThat(Desperation.scoringRound(test.game)).isEqualTo(2);
        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isTrue();
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
        assertThat(Desperation.factor(test.game, test.nekro)).isEqualTo(Desperation.BEHIND_FACTOR);
    }

    @Test
    void weighsAPointMoreWhenTwoPointsBehind() {
        test.points(test.sol, 1);
        assertThat(Desperation.factor(test.game, test.nekro)).isEqualTo(Desperation.FACTOR);

        test.points(test.sol, Desperation.POINTS_BEHIND);
        assertThat(Desperation.factor(test.game, test.nekro)).isEqualTo(Desperation.BEHIND_FACTOR);
        assertThat(Desperation.factor(test.game, test.sol)).isEqualTo(Desperation.FACTOR);
    }

    @Test
    void eachKindIsItsOwnExperiment() {
        test.game.setRound(6);
        AiExperiments.disable(test.nekro, Set.of(Experiment.DESPERATION_ACTION_SECRET));

        assertThat(Desperation.forActionSecrets(test.game, test.nekro)).isFalse();
        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isTrue();
        assertThat(Desperation.forStatusSecretSetup(test.game, test.nekro, false))
                .isTrue();

        AiExperiments.disable(test.nekro, Set.of(Experiment.DESPERATION_PUBLIC, Experiment.DESPERATION_STATUS_SECRET));

        assertThat(Desperation.forActionSecrets(test.game, test.nekro)).isTrue();
        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isFalse();
        assertThat(Desperation.forStatusSecretSetup(test.game, test.nekro, false))
                .isFalse();
        assertThat(ActionSecretTiming.pursueNow(test.game, test.nekro, "fwp")).isTrue();
    }
}
