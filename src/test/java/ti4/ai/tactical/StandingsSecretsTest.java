package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.secrets.SecretOdds;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The audit found every AI kept expanding while a rival sat three points from the goal: the leader threat only
// started 1.5 points out, and any held secret counted as a flat 0.35. With WIN_RACE_SECRETS the threat ramps from
// three points out and a rival's secret counts at its card-counting odds.
class StandingsSecretsTest extends BaseTi4Test {

    private DefenseScenario board;
    private Player sardakk;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        sardakk = board.sardakk;
        board.test.game.setVp(10);
    }

    private Standings standings() {
        return new DefenseOutlook(board.test.game, board.test.nekro).standings();
    }

    private void score(Player player, int points) {
        board.test.game.scorePublicObjective(player.getUserID(), board.test.game.addCustomPO("Test points", points));
    }

    private void disableForNekro() {
        AiExperiments.disable(board.test.nekro, Set.of(Experiment.WIN_RACE_SECRETS));
    }

    // Eight of ten with nothing ready: one third of the way into the three-point window.
    @Test
    void aRivalTwoPointsFromTheGoalIsAlreadyAThreat() {
        score(sardakk, 8);

        assertThat(standings().winThreat(sardakk)).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(standings().denialWeight(sardakk))
                .isGreaterThan(0.4 * standings().hurtWeight(sardakk));
    }

    @Test
    void withTheExperimentDisabledTheThreatStartsOnlyOnePointAndAHalfOut() {
        score(sardakk, 8);
        disableForNekro();

        assertThat(standings().winThreat(sardakk)).isZero();
    }

    // Seven of ten is three points out: still calm.
    @Test
    void aRivalThreePointsBehindTheGoalStaysCalm() {
        score(sardakk, 7);

        assertThat(standings().winThreat(sardakk)).isZero();
    }

    @Test
    void aRivalsSecretCountsAtItsCardCountingOdds() {
        sardakk.setSecret("dhw");

        double odds = SecretOdds.chanceReady(board.test.game, board.test.nekro, sardakk);
        assertThat(standings().secretPoint(sardakk)).isCloseTo(odds, within(1e-9));
        assertThat(odds).isNotCloseTo(Standings.SECRET_CHANCE, within(1e-6));
    }

    @Test
    void withTheExperimentDisabledASecretIsAFlatChance() {
        sardakk.setSecret("dhw");
        disableForNekro();

        assertThat(standings().secretPoint(sardakk)).isCloseTo(Standings.SECRET_CHANCE, within(1e-9));
    }
}
