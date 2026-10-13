package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// The chance of meeting two public objectives by the status phase, which is what Imperial turns into an extra point.
// The per-objective chances come from the round-one harness's calibration (round 2, 48 games): a spend objective
// affordable at the start of the action phase was still paid at status 72% of the time, a board objective one step
// short was met 44% of the time and three or more steps short 12%.
class ObjectiveOddsTest extends BaseTi4Test {

    private AiTestGame test;

    // Nekro controls Mordai II (4 resources) and holds 4 trade goods: Erect a Monument (8 resources) is affordable.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("strategy");
        test.nekroHome();
        test.nekro.setTg(4);
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    @Test
    void combinesIndependentChances() {
        assertThat(ObjectiveOdds.atLeastTwo(List.of(1.0, 1.0))).isEqualTo(1.0);
        assertThat(ObjectiveOdds.atLeastTwo(List.of(0.5, 0.5))).isCloseTo(0.25, within(1e-9));
        assertThat(ObjectiveOdds.atLeastTwo(List.of(0.9))).isZero();
    }

    // Two spend objectives draw on the same planets and trade goods, so they count as one chance: no second score.
    @Test
    void twoSpendObjectivesAreOneChance() {
        reveal("monument");
        reveal("trade_routes");

        assertThat(ObjectiveOdds.atLeastTwoByStatus(test.game, test.nekro)).isZero();
    }

    // An affordable Monument (0.72) and Expand Borders, six planets short (0.12), give 0.0864; with Monument out of
    // reach (4 resources short, 0.45) the same pair gives 0.054.
    @Test
    void anAffordableSpendAndAFarBoardObjective() {
        reveal("monument");
        reveal("expand_borders");
        assertThat(ObjectiveOdds.atLeastTwoByStatus(test.game, test.nekro)).isCloseTo(0.72 * 0.12, within(1e-9));

        test.nekro.setTg(0);
        assertThat(ObjectiveOdds.atLeastTwoByStatus(test.game, test.nekro)).isCloseTo(0.45 * 0.12, within(1e-9));
    }

    // Imperial played at once locks in an objective met now, so its extra point only needs one more by status:
    // Monument is scored with Imperial and Expand Borders (0.12) is the extra. Monument alone gains nothing, since
    // the status phase would score it anyway; with nothing met now Imperial needs two by status.
    @Test
    void imperialLocksInAnObjectiveMetNow() {
        reveal("monument");
        assertThat(ObjectiveOdds.extraScoreWithImperial(test.game, test.nekro)).isZero();

        reveal("expand_borders");
        assertThat(ObjectiveOdds.extraScoreWithImperial(test.game, test.nekro)).isCloseTo(0.12, within(1e-9));

        test.nekro.setTg(0);
        assertThat(ObjectiveOdds.extraScoreWithImperial(test.game, test.nekro)).isCloseTo(0.45 * 0.12, within(1e-9));
    }
}
