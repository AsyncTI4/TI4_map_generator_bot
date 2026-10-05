package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class MapGeneratorFrameTest extends BaseTi4Test {

    // Height of the fixed strip under a map-only image.
    private static final int STRIP = 600;
    // The 600x600 tile image has this much padding around the hex.
    private static final int TILE_PADDING = 100;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("map-generator-frame-test");
        // Centre, ring 1 and one lone system on the north edge of ring 4: the canvas is sized for 4 rings,
        // but the placed tiles only span the upper half of it.
        MapFrame.positionsWithin("000", 1).forEach(position -> game.setTile(new Tile("19", position)));
        game.setTile(new Tile("20", "401"));
    }

    // A full 7-hex cluster around 1201 (far north) and a lone system at 1237 (far south): ~7000px apart.
    private static Game twoFarApartClusters() {
        Game twoMaps = new Game();
        twoMaps.newGameSetup();
        twoMaps.setName("two-maps-test");
        twoMaps.setFowMode(true);
        MapFrame.positionsWithin("1201", 1).forEach(position -> twoMaps.setTile(new Tile("19", position)));
        twoMaps.setTile(new Tile("20", "1237"));
        return twoMaps;
    }

    private static MapGenerator render(Game game, DisplayType type, String segment) {
        return new MapGenerator(game, type, null, segment);
    }

    @Test
    void segmentTitleIsSwappedForTheEasterEggOnlyOnARollOfZero() {
        assertTrue(MapGenerator.isEasterEggRoll(0));
        assertFalse(MapGenerator.isEasterEggRoll(1));
        assertFalse(MapGenerator.isEasterEggRoll(199));
    }

    @Test
    void nonFogMapKeepsTheClassicRingBasedCanvas() {
        try (MapGenerator generator = render(game, DisplayType.map, null)) {
            assertEquals(Math.max(1000, MapGenerator.getMapWidth(game)), generator.imageWidth());
            assertEquals(MapGenerator.getMapHeight(game) + STRIP, generator.imageHeight());
        }
    }

    @Test
    void fogMapIsFramedToThePlacedTiles() {
        int classicHeight;
        try (MapGenerator generator = render(game, DisplayType.map, null)) {
            classicHeight = generator.imageHeight();
        }

        game.setFowMode(true);
        try (MapGenerator generator = render(game, DisplayType.map, null)) {
            assertTrue(generator.imageHeight() < classicHeight, "empty outer rings are dropped");
            assertTrue(generator.imageWidth() < Math.max(1000, MapGenerator.getMapWidth(game)), "narrower too");
            assertTrue(generator.imageWidth() >= 11 * 150, "the 10-point score track still fits");
            generator.draw();
        }
    }

    @Test
    void classicMapLayoutOptionTurnsFramingAndSectorsOff() {
        game.setFowMode(true);
        game.setFowOption(FOWOption.CLASSIC_MAP_LAYOUT, true);
        MapSegment.put(game, new MapSegment("core", "000", 1));
        try (MapGenerator generator = render(game, DisplayType.map, "core")) {
            assertEquals(Math.max(1000, MapGenerator.getMapWidth(game)), generator.imageWidth());
            assertEquals(MapGenerator.getMapHeight(game) + STRIP, generator.imageHeight());
            assertNull(generator.shownSegmentName(), "sectors are ignored");
        }
    }

    @Test
    void combinedViewUsesTheClassicWidthMatchingTheFramedMapSize() {
        game.setFowMode(true);
        try (MapGenerator generator = render(game, DisplayType.all, null)) {
            assertEquals(MapGenerator.getMapWidth(game, 3), generator.imageWidth());
        }

        // A small far-away sector gets the same normal width, not the width of a "12-ring" map.
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));
        try (MapGenerator generator = render(twoMaps, DisplayType.all, "south")) {
            assertEquals(MapGenerator.getMapWidth(twoMaps, 3), generator.imageWidth());
            generator.draw();
        }
    }

    @Test
    void requestedSegmentIsShownAndOnlyItsTilesAreDrawn() {
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("north", "1201", 1));
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));

        try (MapGenerator generator = render(twoMaps, DisplayType.map, "south")) {
            assertEquals("south", generator.shownSegmentName());
            assertTrue(generator.isInShownRegion("1237"));
            assertFalse(generator.isInShownRegion("1201"), "the other sector is not drawn");
        }
    }

    @Test
    void gmViewOpensOnTheDefaultSegmentElseTheFirst() {
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("north", "1201", 2));
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));
        try (MapGenerator generator = render(twoMaps, DisplayType.map, null)) {
            assertEquals("north", generator.shownSegmentName());
        }

        MapSegment.setDefault(twoMaps, "south");
        try (MapGenerator generator = render(twoMaps, DisplayType.map, null)) {
            assertEquals("south", generator.shownSegmentName());
        }
    }

    @Test
    void mapsSpreadBeyondNineRingsAreCappedToOneSubMap() {
        try (MapGenerator generator = render(twoFarApartClusters(), DisplayType.map, null)) {
            assertTrue(generator.imageHeight() <= MapFrame.MAX_HEIGHT + STRIP, "within the 9-ring cap");
            assertTrue(generator.imageWidth() <= MapFrame.MAX_WIDTH);
            generator.draw();
        }
    }

    @Test
    void separateFractureIsItsOwnMapAndNeverMixesWithTheGalaxy() {
        game.setFowMode(true);
        for (int index = 1; index <= 7; index++) {
            game.setTile(new Tile("2" + index, "frac" + index));
        }
        game.setTile(new Tile("25", "tl"));
        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);

        try (MapGenerator main = render(game, DisplayType.map, null)) {
            assertTrue(main.isInShownRegion("000"));
            assertFalse(main.isInShownRegion("frac1"));
            main.draw();
        }
        try (MapGenerator fracture = render(game, DisplayType.map, MapSegment.FRACTURE)) {
            assertEquals(MapSegment.FRACTURE, fracture.shownSegmentName());
            assertTrue(fracture.isInShownRegion("frac1"));
            assertFalse(fracture.isInShownRegion("000"));
            assertTrue(fracture.pinnedCorners().isEmpty(), "corner tiles stay out of the Fracture view");
            fracture.draw();
        }
    }

    @Test
    void separateFractureIsShownWhenItIsTheOnlySectorInView() {
        // Nothing but Fracture tiles: before the fix no sector was chosen, the frame fell back to the classic
        // canvas and galaxy and Fracture were drawn together.
        Game fractureOnly = new Game();
        fractureOnly.newGameSetup();
        fractureOnly.setName("fracture-only-test");
        fractureOnly.setFowMode(true);
        for (int index = 1; index <= 7; index++) {
            fractureOnly.setTile(new Tile("2" + index, "frac" + index));
        }
        fractureOnly.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);

        try (MapGenerator generator = render(fractureOnly, DisplayType.map, null)) {
            assertEquals(MapSegment.FRACTURE, generator.shownSegmentName());
            assertTrue(generator.isInShownRegion("frac1"));
            assertFalse(generator.isInShownRegion("000"));
            generator.draw();
        }
    }

    @Test
    void knownCornerTileIsPinnedInsideTheFrameWithItsOwnColumn() {
        game.setFowMode(true);
        int contentWidthWithoutCorner;
        try (MapGenerator generator = render(game, DisplayType.map, null)) {
            contentWidthWithoutCorner = generator.frameContentWidth();
        }

        game.setTile(new Tile("25", "tl"));
        try (MapGenerator generator = render(game, DisplayType.map, null)) {
            assertEquals(Set.of("tl"), generator.pinnedCorners());
            assertTrue(generator.frameContentWidth() > contentWidthWithoutCorner, "a column was added for it");

            Point hex = generator.pinnedCornerTileOrigin("tl");
            hex.translate(TILE_PADDING, TILE_PADDING);
            assertTrue(hex.x >= 0 && hex.x + TileGenerator.TILE_WIDTH <= generator.imageWidth());
            assertTrue(hex.y >= 0 && hex.y + TileGenerator.TILE_HEIGHT <= generator.imageHeight());
            generator.draw();
        }
    }

    @Test
    void pinnedCornersAreDrawnInEverySectorView() {
        Game twoMaps = twoFarApartClusters();
        twoMaps.setTile(new Tile("25", "br"));
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));
        try (MapGenerator generator = render(twoMaps, DisplayType.map, "south")) {
            assertTrue(generator.isInShownRegion("br"));
        }
    }
}
