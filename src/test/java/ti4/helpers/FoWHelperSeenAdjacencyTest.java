package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.map.MapFrame;
import ti4.testUtils.BaseTi4Test;

// The fog feature views (Exile, Aetherstream) must only look through tiles the viewer can see.
class FoWHelperSeenAdjacencyTest extends BaseTi4Test {

    private static final String CENTER = "000";
    private static final String NEIGHBOUR = "101";
    private static final Predicate<String> NEIGHBOUR_HIDDEN = position -> !NEIGHBOUR.equals(position);

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("seen-adjacency-test");
        MapFrame.positionsWithin(CENTER, 2).forEach(position -> game.setTile(new Tile("19", position)));
    }

    @Test
    void anomalyNextDoorOnlyCountsWhenItsTileIsSeen() {
        game.setTile(new Tile("41", NEIGHBOUR));

        assertTrue(FoWHelper.isTileAdjacentToAnAnomaly(game, CENTER, null));
        assertFalse(FoWHelper.isTileAdjacentToAnAnomaly(game, CENTER, null, NEIGHBOUR_HIDDEN));
    }

    @Test
    void exileDestroyerOnlyCountsWhenItsTileIsSeen() {
        Player crimson = game.addPlayer("111", "crimson");
        crimson.setFaction(game, "crimson");
        crimson.setColor("red");
        crimson.setUnitsOwned(Set.of("crimson_destroyer"));
        game.getTileByPosition(NEIGHBOUR)
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, crimson.getColorID()), 1);
        Tile center = game.getTileByPosition(CENTER);

        assertTrue(FoWHelper.isTileInExileRange(game, center, crimson));
        assertFalse(FoWHelper.isTileInExileRange(game, center, crimson, NEIGHBOUR_HIDDEN));
    }
}
