package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.helpers.Units.UnitType;
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

    // With every objective worth 1 VP the expected extra points match the old extra-score chance.
    @Test
    void extraPointsMatchTheOldChanceForOnePointObjectives() {
        reveal("monument");
        reveal("expand_borders");

        assertThat(ObjectiveOdds.extraPointsWithImperial(test.game, test.nekro))
                .isCloseTo(ObjectiveOdds.extraScoreWithImperial(test.game, test.nekro), within(1e-9));
    }

    // Two stage II objectives met: Centralize Galactic Trade (10 trade goods, scored now with Imperial) and Command an
    // Armada (eight ships in one system, 0.9 by status). The extra public is worth 2 VP, so 1.8 expected points where
    // the old odds counted 0.9.
    @Test
    void stageTwoObjectivesCountTwoPoints() {
        test.nekro.setTg(10);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 8);
        reveal("centralize_trade");
        reveal("command_armada");

        assertThat(ObjectiveOdds.extraScoreWithImperial(test.game, test.nekro)).isCloseTo(0.9, within(1e-9));
        assertThat(ObjectiveOdds.extraPointsWithImperial(test.game, test.nekro)).isCloseTo(1.8, within(1e-9));
    }

    // At 8 of 10 VP with two 1 VP publics met, the status phase alone reaches 9; Imperial reaches 10 whenever Raise a
    // Fleet still holds (0.9). Holding Mecatol Rex as well, the Imperial point plus Monument scored with the primary
    // reach 10 for certain, while without Imperial there is no Mecatol Rex point at all.
    @Test
    void winSwingIsTheChanceImperialAloneReachesTheGoal() {
        test.game.setVp(10);
        test.points(test.nekro, 8);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 5);
        reveal("raise_fleet");
        reveal("monument");

        assertThat(ObjectiveOdds.imperialWinSwing(test.game, test.nekro, 0)).isCloseTo(0.9, within(1e-9));
        assertThat(ObjectiveOdds.imperialWinSwing(test.game, test.nekro, 1)).isCloseTo(1.0, within(1e-9));
    }
}
