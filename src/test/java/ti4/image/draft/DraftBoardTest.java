package ti4.image.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class DraftBoardTest extends BaseTi4Test {

    @Test
    void paddingGrowsTheCanvasOnBothSides() {
        Game game = new Game();
        DraftBoard bare = new DraftBoard(0, 0);
        DraftBoard padded = new DraftBoard(50, 150);

        assertEquals(bare.width(game) + 100, padded.width(game));
        assertEquals(bare.height(game) + 300, padded.height(game));
    }

    @Test
    void paddingShiftsTilePositions() {
        Game game = new Game();
        Point bare = new DraftBoard(0, 0).tilePosition(game, "000");
        Point padded = new DraftBoard(50, 150).tilePosition(game, "000");

        assertEquals(bare.x + 50, padded.x);
        assertEquals(bare.y + 150, padded.y);
    }

    @Test
    void cornerPositionsAreNotBoardTiles() {
        Game game = new Game();
        game.setTile(new Tile("18", "000"));
        game.setTile(new Tile("18", "tl"));

        Map<String, Tile> tiles = DraftBoard.tilesOnBoard(game);

        assertEquals(1, tiles.size());
        assertTrue(tiles.containsKey("000"));
    }
}
