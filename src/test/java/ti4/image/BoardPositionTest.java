package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.RegexHelper;
import ti4.testUtils.BaseTi4Test;

class BoardPositionTest extends BaseTi4Test {

    // Maps A-G are fog-only: normal games must keep refusing these positions.
    @Test
    void extraMapPositionsAreValidOnlyInFogGamesAndReachButtonRouting() {
        Game game = new Game();
        game.newGameSetup();
        assertFalse(PositionMapper.isTilePositionValid(game, "a101"));
        assertTrue(PositionMapper.isTilePositionValid(game, "101"));
        game.setFowMode(true);
        assertTrue(PositionMapper.isTilePositionValid(game, "a101"));
        assertTrue(PositionMapper.isTilePositionValid(game, "g848"));

        assertFalse(PositionMapper.isTilePositionValid("h101"), "only maps A to G");
        assertFalse(PositionMapper.isTilePositionValid("a199"), "local position must exist on the main grid");
        assertFalse(PositionMapper.isTilePositionValid("a901"), "maps stop at ring 8");
        assertFalse(BoardPosition.isBoardPosition("br"), "corner positions are not maps");

        // Button handlers parse positions with this regex; map positions must reach them.
        assertTrue("move_a101".matches("move_" + RegexHelper.posRegex()));
    }

    // Hex adjacency never leaks between maps; only wormholes and custom links may connect them.
    @Test
    void adjacencyNeverLeavesTheMap() {
        assertEquals(
                PositionMapper.getAdjacentTilePositions("101").stream()
                        .map(position -> "b" + position)
                        .toList(),
                PositionMapper.getAdjacentTilePositions("b101"));
        List<String> edge = PositionMapper.getAdjacentTilePositions("a801");
        assertTrue(edge.stream().allMatch(position -> "x".equals(position) || position.startsWith("a")));
    }

    // Main ring count drives ring buttons, the map string and stat tiles, so extra maps must not inflate it.
    @Test
    void extraMapsWidenTheLayoutWithoutChangingTheMainRingCountOrOverlapping() {
        Game game = new Game();
        game.newGameSetup();
        game.setFowMode(true);
        game.setTile(new Tile("19", "403"));
        game.setTile(new Tile("19", "a515"));

        assertEquals(4, game.getRingCount());
        assertEquals(5, PositionMapper.layoutRingCount(game));
        assertTrue(scaled(game, "a515").x - scaled(game, "403").x > TileGenerator.TILE_WIDTH, "maps must not overlap");
    }

    private static Point scaled(Game game, String position) {
        Point raw = PositionMapper.getTilePosition(position);
        return PositionMapper.getScaledTilePosition(game, position, raw.x, raw.y);
    }
}
