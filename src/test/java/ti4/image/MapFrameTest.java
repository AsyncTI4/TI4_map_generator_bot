package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
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
        Point raw = PositionMapper.getTilePosition("000");
        Point scaled = PositionMapper.getScaledTilePosition(game, "000", raw.x, raw.y, 0);
        assertEquals((frame.width() - TileGenerator.TILE_WIDTH) / 2, scaled.x + PAD_X - frame.offsetX());
        assertEquals(PAD_Y, scaled.y + PAD_Y - frame.offsetY());
    }

    @Test
    void cornerLabelsAndUnknownPositionsAreIgnored() {
        assertNull(MapFrame.around(game, List.of("tl", "br", "nonsense"), 0, PAD_X, PAD_Y, 1000));
    }

    @Test
    void overCapWindowSlidesToKeepTheHomeSystemButNeverLeavesTheSubMap() {
        Rectangle subMap = new Rectangle(0, 0, 10_000, 10_000);
        // Home system near the right edge: centring on it would reach past the sub-map.
        Rectangle home = new Rectangle(9500, 100, TileGenerator.TILE_WIDTH, TileGenerator.TILE_HEIGHT);

        MapFrame frame = MapFrame.fit(subMap, 1000, home);

        assertEquals(MapFrame.MAX_WIDTH, frame.width());
        assertEquals(MapFrame.MAX_HEIGHT, frame.height());
        assertEquals(subMap.width - MapFrame.MAX_WIDTH, frame.offsetX(), "window slides only to the sub-map edge");
        assertEquals(0, frame.offsetY(), "window does not go above the sub-map");
        Rectangle window = new Rectangle(frame.offsetX(), frame.offsetY(), frame.width(), frame.height());
        assertTrue(window.contains(home), "the home system stays in view");
    }

    @Test
    void reachDecidesHowManyEmptyHexesAGroupMayJump() {
        // Reach = gap + 1. 000-101 touch, 000-201 have one empty ring between them, 000-301 have two.
        assertEquals(Set.of("000", "101"), MapFrame.cluster(Set.of("000", "101"), "000", 1));
        assertEquals(Set.of("000"), MapFrame.cluster(Set.of("000", "201"), "000", 1));
        assertEquals(Set.of("000", "201"), MapFrame.cluster(Set.of("000", "201"), "000", 2));
        assertEquals(Set.of("000"), MapFrame.cluster(Set.of("000", "301"), "000", 2));
    }

    @Test
    void largestClusterWinsWhenThereIsNoHomeSystem() {
        Set<String> north = MapFrame.positionsWithin("1201", 1);
        Set<String> positions = new HashSet<>(north);
        positions.add("1237");
        assertEquals(north, MapFrame.largestCluster(positions, 1, Map.of()));
    }

    @Test
    void anAdjacencyLinkJoinsGroupsThatDoNotTouch() {
        // 000 and 301 are three hexes apart: separate without a link, one group with it.
        Set<String> positions = Set.of("000", "301");
        assertEquals(Set.of("000"), MapFrame.cluster(positions, "000", 1));
        Map<String, Set<String>> links = Map.of("000", Set.of("301"), "301", Set.of("000"));
        assertEquals(positions, MapFrame.cluster(positions, "000", 1, links));
    }
}
