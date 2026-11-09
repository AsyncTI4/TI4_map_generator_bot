package ti4.ai.winnu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.eval.PlanetStake;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Reclamation: taking Mecatol Rex in a tactical action puts a PDS and a space dock there for free (the bot places them
// itself), so the planner adds what those structures are worth to the value of taking it.
class ReclamationTest extends BaseTi4Test {

    private final WinnuTestBoard board = new WinnuTestBoard();

    @Test
    void takingMecatolIsWorthAFreeSpaceDockAndPds() {
        assertThat(Reclamation.captureBonus(board.test.game, board.winnu))
                .isEqualTo(PlanetStake.SPACE_DOCK + PlanetStake.PDS);
    }

    // Holding Mecatol already, there is nothing left to gain from taking it.
    @Test
    void noBonusOnceMecatolIsHeld() {
        board.holdMecatol();

        assertThat(Reclamation.captureBonus(board.test.game, board.winnu)).isZero();
    }

    @Test
    void noBonusWithoutTheAbility() {
        assertThat(Reclamation.captureBonus(board.test.game, board.test.sol)).isZero();
    }

    // With every space dock already on the board, only the PDS still counts.
    @Test
    void onlyStructuresLeftInReinforcementsCount() {
        board.winnu.setUnitCap(Reclamation.SPACE_DOCK, 1);
        board.test.units(board.test.place("26", "101"), "lodor", board.winnu, UnitType.Spacedock, 1);

        assertThat(Reclamation.captureBonus(board.test.game, board.winnu)).isEqualTo(PlanetStake.PDS);
    }
}
