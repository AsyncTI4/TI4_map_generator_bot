package ti4.helpers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ButtonHelperCapacityTest extends BaseTi4Test {
    private static final String PLAYER_COLOR = "red";

    private Tile createFlagshipTile() {
        Tile tile = new Tile("flagship tile", null, null, null, null);
        tile.getSpaceUnitHolder().addUnit(new UnitKey(UnitType.Flagship, PLAYER_COLOR), 1);
        return tile;
    }

    private Game createTfGame() {
        Game game = new Game();
        game.setTwilightsFallMode(true);
        return game;
    }

    private Player createPlayer(Game game) {
        Player player = new Player("101", "testUser", game);
        player.setColor(PLAYER_COLOR);
        return player;
    }

    @Test
    void testTfFlagshipCapacity() {
        Tile tile = createFlagshipTile();
        Game game = createTfGame();
        Player player = createPlayer(game);

        // red tf vanilla base flagship, 3 capacity
        player.addOwnedUnitByID("redtf_flagship");
        // nomad flagship unit upgrade from vanilla tf, increases capacity by 2
        player.addOwnedUnitByID("tf-echoofascension");

        int[] fleetAndCapacity = ButtonHelper.checkFleetAndCapacity(player, game, tile);
        int fleetUsed = fleetAndCapacity[0];
        int capacityUsed = fleetAndCapacity[1];
        int capacityTotal = fleetAndCapacity[2];
        int dockedFighters = fleetAndCapacity[3];
        int fighter2s = fleetAndCapacity[4];

        assertEquals(5, capacityTotal, "Tf Flagship Capacity Mismatch");
    }

    @Test
    void testTkFlagshipCapacity() {
        Tile tile = createFlagshipTile();
        Game game = createTfGame();
        Player player = createPlayer(game);

        // Twilight Kart
        game.setTkDestroyerCup(true);
        game.setTkNovaCup(true);

        // pink tk nova base flagship, 6 capacity
        player.addOwnedUnitByID("tk-thevisionsofjanovet");
        // flagship unit upgrade from tk destroyer, should not affect capacity
        player.addOwnedUnitByID("tk-shellofloncara");

        int[] fleetAndCapacity = ButtonHelper.checkFleetAndCapacity(player, game, tile);
        int fleetUsed = fleetAndCapacity[0];
        int capacityUsed = fleetAndCapacity[1];
        int capacityTotal = fleetAndCapacity[2];
        int dockedFighters = fleetAndCapacity[3];
        int fighter2s = fleetAndCapacity[4];

        assertEquals(6, capacityTotal, "Tk Flagship Capacity Mismatch");
    }
}
