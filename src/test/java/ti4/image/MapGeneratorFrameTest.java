package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class MapGeneratorFrameTest extends BaseTi4Test {

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("map-generator-frame-test");
        // Centre, ring 1 and one lone system on the north edge of ring 4: the canvas is sized for 4 rings,
        // but the placed tiles only span the upper half of it.
        for (String position : MapFrame.positionsWithin("000", 1)) {
            game.setTile(new Tile("19", position));
        }
        game.setTile(new Tile("20", "401"));
    }

    private static final int LABEL_SPACE = 150;

    @SuppressWarnings("unchecked")
    private static Set<String> drawnSegmentPositions(MapGenerator generator) throws ReflectiveOperationException {
        Field field = MapGenerator.class.getDeclaredField("segmentDrawPositions");
        field.setAccessible(true);
        return (Set<String>) field.get(generator);
    }

    private static BufferedImage canvas(MapGenerator generator) throws ReflectiveOperationException {
        Field field = MapGenerator.class.getDeclaredField("mainImage");
        field.setAccessible(true);
        return (BufferedImage) field.get(generator);
    }

    private static int classicWidthForRings(Game game, int rings) throws ReflectiveOperationException {
        Method width = MapGenerator.class.getDeclaredMethod("getMapWidth", Game.class, int.class);
        width.setAccessible(true);
        return (int) width.invoke(null, game, rings);
    }

    private static int classicSize(String method, Game game) throws ReflectiveOperationException {
        Method size = MapGenerator.class.getDeclaredMethod(method, Game.class);
        size.setAccessible(true);
        return (int) size.invoke(null, game);
    }

    @Test
    void nonFogMapKeepsTheClassicRingBasedCanvas() throws ReflectiveOperationException {
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            BufferedImage image = canvas(generator);
            int expectedWidth = Math.max(1000, classicSize("getMapWidth", game));
            assertEquals(expectedWidth, image.getWidth());
            assertEquals(classicSize("getMapHeight", game) + 600, image.getHeight());
        }
    }

    @Test
    void fogMapIsFramedToThePlacedTilesAndGetsShorter() throws ReflectiveOperationException {
        int classicHeight;
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            classicHeight = canvas(generator).getHeight();
        }

        game.setFowMode(true);
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            BufferedImage image = canvas(generator);
            assertTrue(image.getHeight() < classicHeight, "fog map should drop the empty outer rings");
            int classicWidth = Math.max(1000, classicSize("getMapWidth", game));
            assertTrue(image.getWidth() < classicWidth, "fog map-only view should also get narrower");
            // The 10-point score track (11 boxes of 150px) is the widest thing under the map here.
            assertTrue(image.getWidth() >= 11 * 150, "the score track must still fit");
            generator.draw();
        }
    }

    @Test
    void fogCombinedViewUsesTheClassicWidthOfTheFramedMapSize() throws ReflectiveOperationException {
        game.setFowMode(true);
        try (MapGenerator generator = new MapGenerator(game, DisplayType.all, null)) {
            // The placed tiles are only 3 hexes wide, so the player areas get the normal 3-ring width.
            assertEquals(classicWidthForRings(game, 3), canvas(generator).getWidth());
            assertTrue(canvas(generator).getWidth() < classicSize("getMapWidth", game));
        }
    }

    @Test
    void farAwaySegmentGetsANormalSizedCombinedImage() throws ReflectiveOperationException {
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));
        try (MapGenerator generator = new MapGenerator(twoMaps, DisplayType.all, null, "south")) {
            assertEquals(classicWidthForRings(twoMaps, 3), canvas(generator).getWidth());
            generator.draw();
        }
    }

    @Test
    void separateFractureLeavesTheMainMapAndIsItsOwnSegment() throws ReflectiveOperationException {
        game.setFowMode(true);
        for (int index = 1; index <= 7; index++) {
            game.setTile(new Tile("2" + index, "frac" + index));
        }
        int withFracture;
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            withFracture = canvas(generator).getHeight();
        }

        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            assertTrue(canvas(generator).getHeight() < withFracture, "the Fracture moved out of the main map");
            generator.draw();
        }
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null, MapSegment.FRACTURE)) {
            assertTrue(canvas(generator).getHeight() < withFracture, "the Fracture on its own is smaller too");
            generator.draw();
        }
    }

    @Test
    void gmSegmentOverridesTheAutomaticFrame() throws ReflectiveOperationException {
        game.setFowMode(true);
        int autoHeight;
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            autoHeight = canvas(generator).getHeight();
        }

        MapSegment.put(game, new MapSegment("core", "000", 3));
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            assertTrue(canvas(generator).getHeight() > autoHeight, "a 3-ring segment is taller than the auto frame");
        }
    }

    @Test
    void requestedSegmentIsRenderedOnItsOwn() throws ReflectiveOperationException {
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("north", "1201", 1));
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));

        try (MapGenerator generator = new MapGenerator(twoMaps, DisplayType.map, null, "south")) {
            // One 3-hex-tall segment, padding, room for the "Map: south" label, plus the strip under the map.
            int segmentHeight = 3 * TileGenerator.TILE_HEIGHT + 2 * 200 + LABEL_SPACE;
            assertEquals(segmentHeight + 600, canvas(generator).getHeight());
            assertEquals(MapFrame.positionsWithin("1237", 1), drawnSegmentPositions(generator));
        }
    }

    @Test
    void gmViewOpensOnTheDefaultSegment() throws ReflectiveOperationException {
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("north", "1201", 2));
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));

        int firstSegmentHeight;
        try (MapGenerator generator = new MapGenerator(twoMaps, DisplayType.map, null)) {
            firstSegmentHeight = canvas(generator).getHeight();
        }

        MapSegment.setDefault(twoMaps, "south");
        try (MapGenerator generator = new MapGenerator(twoMaps, DisplayType.map, null)) {
            int southHeight = 3 * TileGenerator.TILE_HEIGHT + 2 * 200 + LABEL_SPACE + 600;
            assertEquals(southHeight, canvas(generator).getHeight());
            assertTrue(southHeight < firstSegmentHeight, "without a default the GM sees the first segment");
        }
    }

    @Test
    void mapsSpreadBeyondNineRingsAreCappedToOneSubMap() throws ReflectiveOperationException {
        Game twoMaps = twoFarApartClusters();
        try (MapGenerator generator = new MapGenerator(twoMaps, DisplayType.map, null)) {
            BufferedImage image = canvas(generator);
            assertTrue(image.getHeight() <= MapFrame.MAX_HEIGHT + 600, "map section stays within the 9-ring cap");
            assertTrue(image.getWidth() <= MapFrame.MAX_WIDTH);
            generator.draw();
        }
    }

    // A full 7-hex cluster around 1201 (far north) and a lone system at 1237 (far south): ~7000px apart.
    private static Game twoFarApartClusters() {
        Game twoMaps = new Game();
        twoMaps.newGameSetup();
        twoMaps.setName("two-maps-test");
        twoMaps.setFowMode(true);
        for (String position : MapFrame.positionsWithin("1201", 1)) {
            twoMaps.setTile(new Tile("19", position));
        }
        twoMaps.setTile(new Tile("20", "1237"));
        return twoMaps;
    }
}
