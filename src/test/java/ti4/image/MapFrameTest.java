package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.settings.users.UserSettings;
import ti4.testUtils.BaseTi4Test;

class MapFrameTest extends BaseTi4Test {

    private static final int PAD_X = 300;
    private static final int PAD_Y = 200;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("map-frame-test");
    }

    private Point scaled(String position) {
        Point raw = PositionMapper.getTilePosition(position);
        return PositionMapper.getScaledTilePosition(game, position, raw.x, raw.y, 0);
    }

    @Test
    void radiusZeroIsJustTheCentreAndRadiusOneAddsTheFirstRing() {
        assertEquals(Set.of("000"), MapFrame.positionsWithin("000", 0));
        assertEquals(Set.of("000", "101", "102", "103", "104", "105", "106"), MapFrame.positionsWithin("000", 1));
    }

    @Test
    void singleTileFrameIsOneHexPlusPaddingAndCentredInTheMinimumWidth() {
        MapFrame frame = MapFrame.around(game, List.of("000"), 0, PAD_X, PAD_Y, 2000);
        assertNotNull(frame);
        assertEquals(TileGenerator.TILE_HEIGHT + 2 * PAD_Y, frame.height());
        assertEquals(2000, frame.width());

        // The hex's left edge after the offset is applied, as MapGenerator.addTile draws it.
        int hexLeft = scaled("000").x + PAD_X - frame.offsetX();
        int hexTop = scaled("000").y + PAD_Y - frame.offsetY();
        assertEquals((frame.width() - TileGenerator.TILE_WIDTH) / 2, hexLeft);
        assertEquals(PAD_Y, hexTop);
    }

    @Test
    void frameGrowsBeyondTheMinimumWidthWhenTheTilesNeedIt() {
        MapFrame frame = MapFrame.around(game, MapFrame.positionsWithin("000", 3), 0, PAD_X, PAD_Y, 100);
        assertNotNull(frame);
        assertTrue(frame.width() > 100);
    }

    @Test
    void cornerLabelsAndUnknownPositionsAreIgnored() {
        assertNull(MapFrame.around(game, List.of("tl", "br", "nonsense"), 0, PAD_X, PAD_Y, 1000));
    }

    @Test
    void gmFrameIsReadFromTheGameAndClearedAgain() {
        assertNull(MapFrame.gmFramePositions(game));

        MapFrame.setGmFrame(game, "000", 1);
        assertEquals(7, MapFrame.gmFramePositions(game).size());

        MapFrame.clearGmFrame(game);
        assertNull(MapFrame.gmFramePositions(game));
    }

    @Test
    void invalidGmFrameFallsBackToAutomaticFraming() {
        game.setStoredValue("fowMapFrame", "nowhere:2");
        assertNull(MapFrame.gmFramePositions(game));
        game.setStoredValue("fowMapFrame", "000:many");
        assertNull(MapFrame.gmFramePositions(game));
    }

    @Test
    void splitMapRefreshIsOffByDefault() {
        assertFalse(new UserSettings().isPrefersSplitMapRefresh());
    }
}
