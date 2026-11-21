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

    // The extra points are the gain over the status phase alone (LRR 61.6: one public per status phase). Imperial
    // scores Monument now (1 VP for certain) and Expand Borders may follow at status (0.12): 1.12 expected points.
    // Without Imperial the status phase scores the better of the two: 1 - 0.28 x 0.88 = 0.7536.
    @Test
    void extraPointsAreTheGainOverTheStatusPhaseAlone() {
        reveal("monument");
        reveal("expand_borders");

        assertThat(ObjectiveOdds.extraPointsWithImperial(test.game, test.nekro))
                .isCloseTo(1.12 - (1 - 0.28 * 0.88), within(1e-9));
    }

    // Two stage II objectives met: Centralize Galactic Trade (10 trade goods, 0.72 by status) and Command an Armada
    // (eight ships in one system, 0.9 by status). Imperial scores Centralize now and Armada by status: 2 + 1.8. The
    // status phase alone scores one 2 VP public with chance 1 - 0.28 x 0.1, so the gain is 3.8 - 1.944 = 1.856.
    @Test
    void stageTwoObjectivesCountTwoPoints() {
        test.nekro.setTg(10);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 8);
        reveal("centralize_trade");
        reveal("command_armada");

        assertThat(ObjectiveOdds.extraScoreWithImperial(test.game, test.nekro)).isCloseTo(0.9, within(1e-9));
        assertThat(ObjectiveOdds.extraPointsWithImperial(test.game, test.nekro))
                .isCloseTo(3.8 - 2 * (1 - 0.28 * 0.1), within(1e-9));
    }

    // The audit's mixed-points overcount. Raise a Fleet (1 VP, met now, 0.9) is the only thing Imperial can lock in;
    // Command an Armada is three ships short (0.12) and Centralize Galactic Trade two trade goods short (0.6), both
    // 2 VP. With Imperial: 1 + 2 x (1 - 0.88 x 0.4) = 2.296. Without it the status phase scores the best one public:
    // P(at least 1 VP) + P(at least 2 VP) = (1 - 0.1 x 0.88 x 0.4) + (1 - 0.88 x 0.4) = 1.6128. The old figure, 1.296,
    // treated Raise a Fleet as already banked and so counted the 2 VP public as extra.
    @Test
    void aLockedStageOneGainsOnlyOverTheBestStatusScore() {
        test.nekro.setTg(8);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 5);
        reveal("raise_fleet");
        reveal("centralize_trade");
        reveal("command_armada");

        double with = 1 + 2 * (1 - 0.88 * 0.4);
        double without = (1 - 0.1 * 0.88 * 0.4) + (1 - 0.88 * 0.4);
        assertThat(ObjectiveOdds.extraPointsWithImperial(test.game, test.nekro))
                .isCloseTo(with - without, within(1e-9));
    }

    // An extra public needs two publics met now or one step short (one scored with the primary, LRR 45.2, and one at
    // status, LRR 61.6). Monument affordable plus Expand Borders six planets short is not an extra public.
    @Test
    void anExtraPublicNeedsTwoPublicsWithinReach() {
        reveal("monument");
        reveal("expand_borders");
        assertThat(ObjectiveOdds.imperialScoresAnExtraPublic(test.game, test.nekro))
                .isFalse();

        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 4);
        reveal("raise_fleet");
        assertThat(ObjectiveOdds.imperialScoresAnExtraPublic(test.game, test.nekro))
                .isTrue();
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

        assertThat(ObjectiveOdds.imperialWinSwing(test.game, test.nekro, 0, 0)).isCloseTo(0.9, within(1e-9));
        assertThat(ObjectiveOdds.imperialWinSwing(test.game, test.nekro, 1, 0)).isCloseTo(1.0, within(1e-9));
    }

    // A secret scored in the same status phase (LRR 61.6: one public and one secret) can reach the goal without
    // Imperial. At 8 of 10 VP holding Mecatol Rex with only Raise a Fleet met (0.9): with Imperial the goal is reached
    // for certain; without it only Raise a Fleet plus a ready secret reach 10. A 50% secret leaves a swing of
    // 1 - 0.5 x 0.9; with no Mecatol Rex the primary adds only the certainty of Raise a Fleet, 0.5 x (1 - 0.9).
    @Test
    void winSwingCountsTheSecretThatReachesTheGoalAnyway() {
        test.game.setVp(10);
        test.points(test.nekro, 8);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 5);
        reveal("raise_fleet");

        assertThat(ObjectiveOdds.imperialWinSwing(test.game, test.nekro, 1, 0)).isCloseTo(1.0, within(1e-9));
        assertThat(ObjectiveOdds.imperialWinSwing(test.game, test.nekro, 1, 0.5))
                .isCloseTo(1 - 0.5 * 0.9, within(1e-9));
        assertThat(ObjectiveOdds.imperialWinSwing(test.game, test.nekro, 0, 0)).isZero();
        assertThat(ObjectiveOdds.imperialWinSwing(test.game, test.nekro, 0, 0.5))
                .isCloseTo(0.5 * (1 - 0.9), within(1e-9));
    }
}
