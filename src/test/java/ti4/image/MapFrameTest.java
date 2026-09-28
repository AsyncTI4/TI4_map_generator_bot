package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.service.option.FOWOptionService.FOWOption;
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
    void cornerLabelsAndUnknownPositionsAreIgnored() {
        assertNull(MapFrame.around(game, List.of("tl", "br", "nonsense"), 0, PAD_X, PAD_Y, 1000));
    }

    @Test
    void frameThatFitsTheCapKeepsItsBounds() {
        Rectangle bounds = new Rectangle(100, 200, 3000, 4000);
        MapFrame frame = MapFrame.fit(bounds, 1000, null);
        assertEquals(new MapFrame(100, 200, 3000, 4000), frame);
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
    void withGapOneClustersJoinAcrossOneEmptyHexButNotAcrossTwo() {
        // Reach 2 = gap 1. 000 and 201 have one empty ring between them, 000 and 301 have two.
        assertEquals(Set.of("000", "201"), MapFrame.cluster(Set.of("000", "201"), "000", 2));
        assertEquals(Set.of("000"), MapFrame.cluster(Set.of("000", "301"), "000", 2));
    }

    @Test
    void withGapZeroAnyEmptyHexSplitsAGroup() {
        // Reach 1 = gap 0, the default: only tiles that touch belong together.
        assertEquals(Set.of("000"), MapFrame.cluster(Set.of("000", "201"), "000", 1));
        assertEquals(Set.of("000", "101"), MapFrame.cluster(Set.of("000", "101"), "000", 1));
    }

    @Test
    void largestClusterWinsWhenThereIsNoHomeSystem() {
        Set<String> north = MapFrame.positionsWithin("1201", 1);
        Set<String> positions = new HashSet<>(north);
        positions.add("1237");
        assertEquals(north, MapFrame.largestCluster(positions, 1));
    }

    @Test
    void segmentsRoundTripThroughTheGame() {
        assertTrue(MapSegment.all(game).isEmpty());

        MapSegment.put(game, new MapSegment("home", "000", 3));
        MapSegment.put(game, new MapSegment("far-north", "1201", 2));
        assertEquals(2, MapSegment.all(game).size());
        assertEquals(
                new MapSegment("far-north", "1201", 2),
                MapSegment.find(game, "far-north").orElseThrow());

        MapSegment.put(game, new MapSegment("home", "101", 4));
        assertEquals(
                new MapSegment("home", "101", 4), MapSegment.find(game, "home").orElseThrow());
        assertEquals(2, MapSegment.all(game).size(), "saving an existing name replaces it");

        assertTrue(MapSegment.remove(game, "home"));
        assertFalse(MapSegment.remove(game, "home"));
        assertEquals(List.of(new MapSegment("far-north", "1201", 2)), MapSegment.all(game));
    }

    @Test
    void defaultSegmentIsStoredAndClearedWhenItsSegmentIsRemoved() {
        MapSegment.put(game, new MapSegment("home", "000", 3));
        MapSegment.put(game, new MapSegment("far", "1201", 2));
        assertTrue(MapSegment.defaultSegment(game).isEmpty());

        MapSegment.setDefault(game, "far");
        assertEquals("far", MapSegment.defaultSegment(game).orElseThrow().name());

        MapSegment.remove(game, "far");
        assertTrue(MapSegment.defaultSegment(game).isEmpty(), "a removed segment cannot stay the default");
        MapSegment.put(game, new MapSegment("far", "1201", 2));
        assertTrue(MapSegment.defaultSegment(game).isEmpty(), "re-adding the name does not restore the default");
    }

    @Test
    void fractureIsABuiltInSegmentOnlyWhenTheOptionIsOn() {
        game.setTile(new Tile("25", "frac1"));
        game.setFowMode(true);
        assertFalse(MapSegment.isFractureSeparate(game));

        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);
        MapSegment fracture = MapSegment.find(game, MapSegment.FRACTURE).orElseThrow();
        assertTrue(fracture.isFracture());
        assertEquals(Set.of("frac1"), fracture.positions());
        assertTrue(MapSegment.stored(game).isEmpty(), "the built-in segment is never stored");
    }

    @Test
    void reservedNamesAreNeverStored() {
        game.setStoredValue("fowMapSegments", "main=000:1;fracture=000:1;ok=000:1");
        assertEquals(List.of(new MapSegment("ok", "000", 1)), MapSegment.stored(game));
    }

    @Test
    void segmentNamesAreShortLowercaseSlugs() {
        assertTrue(MapSegment.isValidName("north-2"));
        assertFalse(MapSegment.isValidName("North"));
        assertFalse(MapSegment.isValidName("with space"));
        assertFalse(MapSegment.isValidName("a".repeat(21)));
        assertFalse(MapSegment.isValidName(""));
    }

    @Test
    void brokenStoredSegmentsAreSkipped() {
        game.setStoredValue("fowMapSegments", "ok=000:2;bad=nowhere:2;big=000:12;Upper=000:1;junk");
        assertEquals(List.of(new MapSegment("ok", "000", 2)), MapSegment.all(game));
    }

    @Test
    void segmentIsVisibleOnlyWhenAKnownSystemLiesInside() {
        MapSegment.put(game, new MapSegment("home", "000", 1));
        MapSegment.put(game, new MapSegment("far", "1201", 1));

        assertEquals(List.of(new MapSegment("home", "000", 1)), MapSegment.visibleFrom(game, Set.of("101")));
        assertTrue(MapSegment.visibleFrom(game, Set.of("501")).isEmpty());
    }

    @Test
    void splitMapRefreshIsOffByDefault() {
        assertFalse(new UserSettings().isPrefersSplitMapRefresh());
    }
}
