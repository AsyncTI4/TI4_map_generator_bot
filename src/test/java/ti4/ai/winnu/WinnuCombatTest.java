package ti4.ai.winnu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.tactical.CombatForecast;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// The Winnu combat edges as the AI's odds model sees them. The flagship's dice are added by the AI itself (the bot
// only counts them when it rolls); the commander's +2 and Imperator's +1 per Support for the Throne come from the
// bot's own combat modifier data, which the shared model already reads.
class WinnuCombatTest extends BaseTi4Test {

    private static final String OPEN_SPACE = "101";

    private final WinnuTestBoard board = new WinnuTestBoard();

    // Salai Sai Corian rolls one die per non-fighter ship it faces: two destroyers and three fighters mean two dice.
    @Test
    void theFlagshipRollsADiePerEnemyNonFighterShip() {
        Side enemy = new Side(board.test.sol, Map.of(UnitType.Destroyer, 2, UnitType.Fighter, 3));

        assertThat(SalaiSaiCorian.dice(Mapper.getUnit(SalaiSaiCorian.FLAGSHIP), 0, enemy))
                .isEqualTo(2);
        assertThat(SalaiSaiCorian.dice(Mapper.getUnit("carrier"), 0, enemy)).isZero();
    }

    // Without its extra dice the flagship would never hit and could not win; with them it beats two destroyers.
    @Test
    void theFlagshipAloneBeatsTwoDestroyers() {
        Tile tile = board.test.place("46", OPEN_SPACE);
        board.test.units(tile, "space", board.winnu, UnitType.Flagship, 1);
        board.test.units(tile, "space", board.test.sol, UnitType.Destroyer, 2);

        double win = CombatForecast.spaceOutlook(board.test.game, board.winnu, OPEN_SPACE)
                .orElseThrow()
                .win();

        assertThat(win).isGreaterThan(0.6);
    }

    // Rickar Rickani: +2 to every combat roll in the Mecatol Rex system.
    @Test
    void theCommanderImprovesTheOddsAtMecatol() {
        board.test.units(board.mecatol, "space", board.winnu, UnitType.Cruiser, 2);
        board.test.units(board.mecatol, "space", board.test.sol, UnitType.Cruiser, 2);
        double without = spaceWin(WinnuTestBoard.MECATOL_POSITION);

        board.unlockLeader("winnucommander");

        assertThat(spaceWin(WinnuTestBoard.MECATOL_POSITION)).isGreaterThan(without + 0.15);
    }

    // Away from Mecatol, the home system and legendary planets the commander does nothing.
    @Test
    void theCommanderDoesNothingInOpenSpace() {
        Tile tile = board.test.place("46", OPEN_SPACE);
        board.test.units(tile, "space", board.winnu, UnitType.Cruiser, 2);
        board.test.units(tile, "space", board.test.sol, UnitType.Cruiser, 2);
        double without = spaceWin(OPEN_SPACE);

        board.unlockLeader("winnucommander");

        assertThat(spaceWin(OPEN_SPACE)).isEqualTo(without);
    }

    // Imperator: +1 to each roll per Support for the Throne in the opponent's play area (even the Winnu's own).
    @Test
    void imperatorCountsTheOpponentsSupportForTheThrone() {
        Tile tile = board.test.place("46", OPEN_SPACE);
        board.test.units(tile, "space", board.winnu, UnitType.Cruiser, 2);
        board.test.units(tile, "space", board.test.sol, UnitType.Cruiser, 2);
        board.winnu.addBreakthrough("winnubt");
        board.winnu.setBreakthroughUnlocked("winnubt", true);
        double without = spaceWin(OPEN_SPACE);

        board.test.sol.addPromissoryNoteToPlayArea(board.winnu.getColor() + "_sftt");

        assertThat(spaceWin(OPEN_SPACE)).isGreaterThan(without);
    }

    private double spaceWin(String position) {
        return CombatForecast.spaceOutlook(board.test.game, board.winnu, position)
                .orElseThrow()
                .win();
    }
}
