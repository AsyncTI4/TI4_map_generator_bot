package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.RegexHelper;
import ti4.image.tile.TileGeometry;
import ti4.testUtils.BaseTi4Test;

class BoardPositionTest extends BaseTi4Test {

    // Maps A-G are fog-only: normal games must keep refusing these positions.
    @Test
    void extraMapPositionsAreValidOnlyInFogGamesAndReachButtonRouting() {
        Game game = new Game();
        game.newGameSetup();
        assertFalse(MapPositionMapper.isTilePositionValid(game, "a101"));
        assertTrue(MapPositionMapper.isTilePositionValid(game, "101"));
        game.setFowMode(true);
        assertTrue(MapPositionMapper.isTilePositionValid(game, "a101"));
        assertTrue(MapPositionMapper.isTilePositionValid(game, "g848"));

        assertFalse(MapPositionMapper.isTilePositionValid("h101"), "only maps A to G");
        assertFalse(MapPositionMapper.isTilePositionValid("a199"), "local position must exist on the main grid");
        assertTrue(MapPositionMapper.isTilePositionValid("a901"));
        assertTrue(MapPositionMapper.isTilePositionValid("g1696"), "maps reach ring 16 like the main map");
        assertFalse(MapPositionMapper.isTilePositionValid("a1697"), "ring 16 has 96 positions");
        assertFalse(MapPositionMapper.isTilePositionValid("a1701"), "maps stop at ring 16");
        assertEquals("1601", BoardPosition.parse("b1601").orElseThrow().local());
        assertFalse(BoardPosition.isBoardPosition("br"), "corner positions are not maps");

        // Button handlers parse positions with this regex; map positions must reach them.
        assertTrue("move_a101".matches("move_" + RegexHelper.posRegex()));
        assertTrue("move_a1601".matches("move_" + RegexHelper.posRegex()));
    }

    // Hex adjacency never leaks between maps; only wormholes and custom links may connect them.
    @Test
    void adjacencyNeverLeavesTheMap() {
        assertEquals(
                MapPositionMapper.getAdjacentTilePositions("101").stream()
                        .map(position -> "b" + position)
                        .toList(),
                MapPositionMapper.getAdjacentTilePositions("b101"));
        for (String rim : List.of("a801", "a1601")) {
            List<String> edge = MapPositionMapper.getAdjacentTilePositions(rim);
            assertTrue(edge.stream().allMatch(position -> "x".equals(position) || position.startsWith("a")), rim);
        }
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
        assertEquals(5, MapPositionMapper.layoutRingCount(game));
        assertTrue(scaled(game, "a515").x - scaled(game, "403").x > TileGeometry.TILE_WIDTH, "maps must not overlap");
    }

    // Below 8 rings the spacing is unchanged; from 9 to 16 rings the maps spread just enough not to overlap,
    // which matters for the GM overview where every map shares one canvas.
    @Test
    void mapsAreSpacedByTheLargestRingInUse() {
        Game game = new Game();
        game.newGameSetup();
        game.setFowMode(true);
        game.setTile(new Tile("19", "403"));
        game.setTile(new Tile("19", "b515"));
        assertEquals(2 * (2 * 5 + 2) * 260, scaled(game, "b101").x - scaled(game, "101").x);

        game.setTile(new Tile("19", "a1201"));
        assertEquals(12, MapPositionMapper.layoutRingCount(game));
        assertEquals((2 * 12 + 2) * 260, scaled(game, "a101").x - scaled(game, "101").x);
        assertTrue(
                scaled(game, "a1250").x - scaled(game, "1219").x >= TileGeometry.TILE_WIDTH, "maps must not overlap");
    }

    private static Point scaled(Game game, String position) {
        Point raw = MapPositionMapper.getTilePosition(position);
        return MapPositionMapper.getScaledTilePosition(game, position, raw.x, raw.y);
    }
}
