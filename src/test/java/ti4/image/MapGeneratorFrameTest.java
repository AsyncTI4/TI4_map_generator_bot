package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
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

    private static BufferedImage canvas(MapGenerator generator) throws ReflectiveOperationException {
        Field field = MapGenerator.class.getDeclaredField("mainImage");
        field.setAccessible(true);
        return (BufferedImage) field.get(generator);
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
    void fogCombinedViewKeepsTheClassicWidthForThePlayerAreas() throws ReflectiveOperationException {
        game.setFowMode(true);
        try (MapGenerator generator = new MapGenerator(game, DisplayType.all, null)) {
            assertEquals(
                    Math.max(1000, classicSize("getMapWidth", game)),
                    canvas(generator).getWidth());
        }
    }

    @Test
    void gmFrameOverridesTheAutomaticFrame() throws ReflectiveOperationException {
        game.setFowMode(true);
        int autoHeight;
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            autoHeight = canvas(generator).getHeight();
        }

        MapFrame.setGmFrame(game, "000", 3);
        try (MapGenerator generator = new MapGenerator(game, DisplayType.map, null)) {
            assertTrue(canvas(generator).getHeight() > autoHeight, "a 3-ring GM frame is taller than 1 ring");
        }
    }
}
