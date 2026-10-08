package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class GalaxyNamesTest extends BaseTi4Test {

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("galaxy-names-test");
        game.setTile(new Tile("19", "000"));
    }

    @Test
    void automaticNamesAreStableUniqueAndTheMainMapIsTheMilkyWay() {
        Map<String, String> names = GalaxyNames.names(game);

        assertEquals(GalaxyNames.IDS, List.copyOf(names.keySet()));
        assertEquals("milky-way", names.get(GalaxyNames.MAIN_ID));
        assertEquals(names.size(), new HashSet<>(names.values()).size(), "every galaxy has its own name");
        assertEquals(names, GalaxyNames.names(game), "same game, same names");
    }

    @Test
    void onlyPlacedMapsCountAsGalaxiesInUse() {
        assertFalse(GalaxyNames.isMultiGalaxy(game));
        assertEquals(List.of(GalaxyNames.MAIN_ID), GalaxyNames.inUse(game));

        game.setTile(new Tile("19", "c000"));
        assertTrue(GalaxyNames.isMultiGalaxy(game));
        assertEquals(List.of(GalaxyNames.MAIN_ID, "c"), GalaxyNames.inUse(game));
    }

    @Test
    void renamesAreValidatedAndKeptUnique() {
        assertNull(GalaxyNames.rename(game, "a", "frontier"));
        assertEquals("frontier", GalaxyNames.name(game, "a"));
        assertTrue(GalaxyNames.isManual(game, "a"));

        assertNotNull(GalaxyNames.rename(game, "b", "frontier"), "already used by a");
        assertNotNull(GalaxyNames.rename(game, "a", "Not Valid"));
        assertNotNull(GalaxyNames.rename(game, "a", "main"), "reserved");
        assertNotNull(GalaxyNames.rename(game, "a", "board-c"), "reserved");
        assertNotNull(GalaxyNames.rename(game, "h", "outer"), "no galaxy h");
    }

    @Test
    void takingAnUnusedGalaxysAutomaticNameMovesThatGalaxyToAFreshOne() {
        // Galaxy b is not on the map, so its automatic name is free to take.
        String bName = GalaxyNames.name(game, "b");

        assertNull(GalaxyNames.rename(game, "a", bName));

        assertEquals(bName, GalaxyNames.name(game, "a"));
        assertNotEquals(bName, GalaxyNames.name(game, "b"));
        Map<String, String> names = GalaxyNames.names(game);
        assertEquals(names.size(), new HashSet<>(names.values()).size());
    }

    @Test
    void theNameOfAGalaxyInUseCannotBeTaken() {
        game.setTile(new Tile("19", "b000"));

        assertNotNull(GalaxyNames.rename(game, "a", GalaxyNames.name(game, "b")));
        assertNotNull(GalaxyNames.rename(game, "a", GalaxyNames.name(game, GalaxyNames.MAIN_ID)));
    }

    @Test
    void namesAreFixedOnceAGalaxyIsPlacedAndNeverShiftAfterwards() {
        game.setTile(new Tile("19", "a000"));
        String main = GalaxyNames.name(game, GalaxyNames.MAIN_ID);
        String a = GalaxyNames.name(game, "a");

        game.setName("renamed-game");
        game.setTile(new Tile("19", "c000"));
        GalaxyNames.rename(game, "c", "outer-rim");

        assertEquals(main, GalaxyNames.name(game, GalaxyNames.MAIN_ID), "main keeps its first name");
        assertEquals(a, GalaxyNames.name(game, "a"), "a keeps its first name");
        assertNotNull(GalaxyNames.rename(game, "c", a), "an assigned name cannot be taken");
    }

    @Test
    void backToAutomaticRestoresTheFirstAssignedName() {
        game.setTile(new Tile("19", "a000"));
        String first = GalaxyNames.name(game, "a");

        GalaxyNames.rename(game, "a", "frontier");
        GalaxyNames.resetToAuto(game, "a");

        assertEquals(first, GalaxyNames.name(game, "a"));
    }

    @Test
    void resetGoesBackToTheAutomaticName() {
        String automatic = GalaxyNames.name(game, "d");
        GalaxyNames.rename(game, "d", "deep-space");

        GalaxyNames.resetToAuto(game, "d");

        assertEquals(automatic, GalaxyNames.name(game, "d"));
        assertFalse(GalaxyNames.isManual(game, "d"));
    }
}
