package ti4.image.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class NeutralPlayerRenderTest extends BaseTi4Test {

    private Game game;
    private Tile tile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("neutral-player-render-test");
        tile = new Tile("25", "101");
        game.setTile(tile);
        Player owner = seat("111", "sol", "blue");
        seat("222", "hacan", "yellow");
        tile.addUnit("quann", Units.getUnitKey(UnitType.Spacedock, owner.getColorID()), 1);
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    @Test
    void drawingUnitsNeverCreatesTheNeutralPlayer() {
        int playersBefore = game.getPlayers().size();

        TileLayers.of(game, null, DisplayType.all).draw(tile, TileStep.Units);

        assertEquals(playersBefore, game.getPlayers().size());
        assertTrue(game.findNeutral().isEmpty());
    }

    @Test
    void findNeutralReturnsTheNeutralPlayerOnceItExists() {
        Player neutral = game.getNeutral();

        assertEquals(neutral, game.findNeutral().orElseThrow());
    }
}
