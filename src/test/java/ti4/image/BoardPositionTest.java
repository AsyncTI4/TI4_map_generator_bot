package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.RegexHelper;
import ti4.testUtils.BaseTi4Test;

class BoardPositionTest extends BaseTi4Test {

    @Test
    void boardPositionsAreALetterAToEFollowedByAMainGridPosition() {
        assertEquals(new BoardPosition('a', "101"), BoardPosition.parse("a101").orElseThrow());
        assertTrue(PositionMapper.isTilePositionValid("a000"));
        assertTrue(PositionMapper.isTilePositionValid("e848"));
        assertTrue(PositionMapper.isTilePositionValid("g101"));
        assertFalse(PositionMapper.isTilePositionValid("h101"), "only seven extra maps, A to G");
        assertFalse(PositionMapper.isTilePositionValid("a199"), "local position must exist on the main grid");
        assertFalse(PositionMapper.isTilePositionValid("a901"), "boards stop at ring 8");
        assertFalse(PositionMapper.isTilePositionValid("afrac1"));
        assertFalse(BoardPosition.isBoardPosition("br"));
        assertFalse(BoardPosition.isBoardPosition("101"));
    }

    @Test
    void boardAdjacencyMirrorsTheMainGridAndNeverLeavesTheBoard() {
        assertEquals(
                PositionMapper.getAdjacentTilePositions("101").stream()
                        .map(position -> "b" + position)
                        .toList(),
                PositionMapper.getAdjacentTilePositions("b101"));
        // Ring 8 neighbours point at ring 9, which boards do not have: they become "x" (no neighbour).
        List<String> edge = PositionMapper.getAdjacentTilePositions("a801");
        assertTrue(edge.contains("x"));
        assertTrue(edge.stream().allMatch(position -> "x".equals(position) || position.startsWith("a")));
    }

    @Test
    void eachBoardSitsFurtherRightOnTheSameRows() {
        Point main = PositionMapper.getTilePosition("101");
        Point a = PositionMapper.getTilePosition("a101");
        Point b = PositionMapper.getTilePosition("b101");

        assertEquals(main.y, a.y);
        assertEquals(BoardPosition.RAW_STRIDE, a.x - main.x);
        assertEquals(BoardPosition.RAW_STRIDE, b.x - a.x);
        assertNull(PositionMapper.getTilePosition("a199"));
    }

    @Test
    void smallerMapsPullBoardsCloserButKeepThemClearOfTheMainGrid() {
        Game game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("19", "403"));
        game.setTile(new Tile("19", "a415"));
        Point mainEdge = scaled(game, "403");
        Point boardEdge = scaled(game, "a415");

        assertTrue(boardEdge.x - mainEdge.x > TileGenerator.TILE_WIDTH, "boards must not overlap");
        assertTrue(boardEdge.x - mainEdge.x < BoardPosition.RAW_STRIDE, "boards move in on smaller maps");
    }

    @Test
    void aBoardBiggerThanTheMainMapSetsTheRingCount() {
        Game game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("19", "301"));
        game.setTile(new Tile("19", "b501"));

        assertEquals(5, game.getRingCount());
    }

    @Test
    void staticPositionRegexAcceptsBoardPositions() {
        assertTrue("move_a101".matches("move_" + RegexHelper.posRegex()));
        assertFalse("move_h101".matches("move_" + RegexHelper.posRegex()));
    }

    @Test
    void boardsInUseComeFromPlacedTiles() {
        Game game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("19", "000"));
        game.setTile(new Tile("19", "c000"));
        game.setTile(new Tile("19", "A101"));

        assertEquals(Set.of('a', 'c'), BoardPosition.boardsInUse(game));
    }

    private static Point scaled(Game game, String position) {
        Point raw = PositionMapper.getTilePosition(position);
        return PositionMapper.getScaledTilePosition(game, position, raw.x, raw.y);
    }
}
