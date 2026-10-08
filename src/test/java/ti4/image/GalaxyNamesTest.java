package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
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

    // A galaxy's name must never change once assigned: not when the game is renamed, not when other galaxies
    // appear or are renamed, and "back to automatic" restores the first name.
    @Test
    void namesStayFixedOnceAssigned() {
        game.setTile(new Tile("19", "a000"));
        String main = GalaxyNames.name(game, GalaxyNames.MAIN_ID);
        String a = GalaxyNames.name(game, "a");
        assertEquals("milky-way", main);

        game.setName("renamed-game");
        game.setTile(new Tile("19", "c000"));
        GalaxyNames.rename(game, "a", "frontier");
        GalaxyNames.resetToAuto(game, "a");

        assertEquals(main, GalaxyNames.name(game, GalaxyNames.MAIN_ID));
        assertEquals(a, GalaxyNames.name(game, "a"));
        Map<String, String> names = GalaxyNames.names(game);
        assertEquals(names.size(), new HashSet<>(names.values()).size(), "every galaxy has its own name");
    }

    @Test
    void renamesAreValidatedAndCannotTakeAnotherGalaxysName() {
        game.setTile(new Tile("19", "b000"));

        assertNull(GalaxyNames.rename(game, "a", "frontier"));
        assertNotNull(GalaxyNames.rename(game, "c", "frontier"), "already used by a");
        assertNotNull(GalaxyNames.rename(game, "a", GalaxyNames.name(game, "b")), "b is in use");
        assertNotNull(GalaxyNames.rename(game, "a", "Not Valid"));
        assertNotNull(GalaxyNames.rename(game, "a", "main"), "reserved");
        assertNotNull(GalaxyNames.rename(game, "h", "outer"), "no galaxy h");
    }
}
