package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.promissory.PlayAreaNotes;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The custodians point is priced like any other point the seat scores: the winning one is worth the win.
class WinAwareCustodiansTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile rex;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setPhaseOfGame("action");
        test.nekroHome();
        rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
    }

    private void holdSolsThrone() {
        test.sol.addOwnedPromissoryNoteByID("blue_sftt");
        test.nekro.setPromissoryNote("blue_sftt");
        test.nekro.addPromissoryNoteToPlayArea("blue_sftt");
    }

    @Test
    void theWinningCustodiansPointIsWorthTheWin() {
        test.points(test.nekro, 9);

        assertThat(new TacticalPlanner.Context(test.game, test.nekro).custodiansValue(rex))
                .isEqualTo(ScoringStakes.WIN_VALUE);
    }

    @Test
    void withTheExperimentOffTheCustodiansPointStaysFlat() {
        test.points(test.nekro, 9);
        AiExperiments.disable(test.nekro, EnumSet.of(AiExperiments.Experiment.WIN_AWARE_POINTS));

        assertThat(new TacticalPlanner.Context(test.game, test.nekro).custodiansValue(rex))
                .isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    @Test
    void anEarlyCustodiansPointKeepsTheFlatFloor() {
        assertThat(new TacticalPlanner.Context(test.game, test.nekro).custodiansValue(rex))
                .isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Sol has a ship in Mecatol Rex's system, so activating it hands Sol's Support for the Throne back: the
    // custodians point would only replace it, which nets nothing.
    @Test
    void aCustodiansPointThatOnlyReplacesAReturnedThroneIsWorthNothing() {
        test.points(test.nekro, 8);
        holdSolsThrone();
        test.units(rex, "space", test.sol, UnitType.Destroyer, 1);

        assertThat(PlayAreaNotes.pointsReturnedOnActivating(test.game, test.nekro, rex))
                .isEqualTo(1);
        assertThat(new TacticalPlanner.Context(test.game, test.nekro).custodiansValue(rex))
                .isZero();
    }

    @Test
    void theThroneIsKeptWhereItsOwnerHasNoUnits() {
        test.points(test.nekro, 8);
        holdSolsThrone();

        assertThat(PlayAreaNotes.pointsReturnedOnActivating(test.game, test.nekro, rex))
                .isZero();
        assertThat(new TacticalPlanner.Context(test.game, test.nekro).custodiansValue(rex))
                .isEqualTo(ScoringStakes.WIN_VALUE);
    }
}
