package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

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
// when a rival is 2+ points ahead or can win this round. It cools once the seat is in position to score that kind,
// once it can no longer act this round, and for an action secret with no target on the board.
class DesperationTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
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
        assertThat(Desperation.actionSecretFactor(test.game, test.nekro, "fwp")).isEqualTo(1.0);

        test.game.setRound(5);
        assertThat(Desperation.actionSecretFactor(test.game, test.nekro, "fwp")).isEqualTo(Desperation.FACTOR);
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

    // A passed seat can no longer complete a public or set up a status secret this round, but it can still score an
    // action secret on another player's turn (Turn Their Fleets to Dust from defensive space cannon).
    @Test
    void coolsOnceTheSeatHasPassed() {
        test.game.setRound(5);
        test.game.setPhaseOfGame("action");
        test.nekro.setTacticalCC(2);
        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isTrue();

        test.nekro.setPassed(true);

        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isFalse();
        assertThat(Desperation.forStatusSecretSetup(test.game, test.nekro, false))
                .isFalse();
        assertThat(Desperation.forActionSecrets(test.game, test.nekro)).isTrue();
    }

    // Activating a system spends the last tactic token first; the moves, invasion and explores that follow are still
    // part of that action.
    @Test
    void staysDesperateThroughItsOwnLastAction() {
        test.game.setRound(5);
        test.game.setPhaseOfGame("action");
        test.nekro.setTacticalCC(0);
        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isFalse();

        test.game.setActivePlayerID(test.nekro.getUserID());

        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isTrue();
    }

    // Without a tactic token the seat can still complete an objective with its unplayed strategy card's primary.
    @Test
    void coolsWithoutATacticTokenOrAnUnplayedStrategyCard() {
        test.game.setRound(5);
        test.game.setPhaseOfGame("action");
        test.nekro.setTacticalCC(0);
        test.nekro.addSC(6);
        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isTrue();

        test.game.setSCPlayed(6, true);

        assertThat(Desperation.forPublic(test.game, test.nekro, false)).isFalse();
    }

    // Destroy Their Greatest Ship needs a rival flagship or war sun on the board; with none, chasing it now cannot
    // score this round, so it is not chased just because the game is late.
    @Test
    void doesNotChaseAnActionSecretWithNoTargetOnTheBoard() {
        test.game.setRound(5);
        test.nekro.setSecret("dtgs");

        assertThat(Desperation.forActionSecrets(test.game, test.nekro)).isTrue();
        assertThat(Desperation.forActionSecret(test.game, test.nekro, "dtgs")).isFalse();
        assertThat(Desperation.actionSecretFactor(test.game, test.nekro, "dtgs"))
                .isEqualTo(1.0);
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
