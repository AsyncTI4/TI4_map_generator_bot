package ti4.service.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// A dreadnought (move 1) moves from two systems away, so the summary flags that the distance exceeds its
// move value and names the one-ship +1 sources that could cover it: Gravity Drive and Tempesta's
// Ionian Fuel Refinery.
class OneShipMoveBonusNoteTest extends BaseTi4Test {

    private Game game;
    private Player player;
    private Tile twoAway;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("one-ship-move-bonus-note");
        player = game.addPlayer("letnevId", "Letnev");
        player.setFaction(game, "letnev");
        player.setColor("red");
        player.setUnitsOwned(new HashSet<>(List.of("dreadnought")));

        game.setTile(new Tile("20", "000"));
        game.setActiveSystem("000");
        game.setTile(new Tile("19", "101"));
        twoAway = new Tile("21", "201");
        game.setTile(twoAway);

        Map<UnitKey, List<Integer>> unitStates = new HashMap<>();
        unitStates.put(
                Units.getUnitKey(UnitType.Dreadnought, player.getColorID()), new ArrayList<>(List.of(1, 0, 0, 0)));
        game.getTacticalActionDisplacement().put(twoAway.getPosition() + "-space", unitStates);
    }

    @Test
    void gravityDriveOnly() {
        player.addTech("gd");

        assertThat(summary())
                .contains("(2 > 1), may have used _Gravity Drive_)")
                .doesNotContain("Tempesta");
    }

    @Test
    void gravityDriveAndReadyTempesta() {
        player.addTech("gd");
        player.addPlanet("tempesta");

        assertThat(summary()).contains("(2 > 1), may have used _Gravity Drive_ or _Tempesta_)");
    }

    @Test
    void readyTempestaWithoutGravityDrive() {
        player.addPlanet("tempesta");

        assertThat(summary()).contains("(2 > 1), may have used _Tempesta_)").doesNotContain("does not have");
    }

    @Test
    void tempestaExhaustedDuringThisTacticalAction() {
        player.addPlanet("tempesta");
        player.exhaustPlanetAbility("tempesta");
        game.setStoredValue("tempestaUsed", player.getFaction());

        assertThat(summary()).contains("may have used _Tempesta_)");
    }

    @Test
    void tempestaExhaustedEarlierDoesNotCount() {
        // tempestaUsed is cleared when the next system is activated, so an exhausted ability without it
        // was spent on an earlier tactical action.
        player.addPlanet("tempesta");
        player.exhaustPlanetAbility("tempesta");

        assertThat(summary()).contains("__does not have _Gravity Drive___").doesNotContain("Tempesta");
    }

    private String summary() {
        return TacticalActionOutputService.buildMessageForSingleSystem(game, player, twoAway);
    }
}
