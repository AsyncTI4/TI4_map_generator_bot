package ti4.image.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.testUtils.BaseTi4Test;

class TraitsStepTest extends BaseTi4Test {

    private Game game;
    private Tile tile;
    private Planet planet;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("traits-step-test");
        tile = new Tile("25", "101");
        game.setTile(tile);
        planet = (Planet) tile.getUnitHolders().get("quann");
    }

    @Test
    void drawingTheTraitIconOfAPlanetWithoutATraitListLeavesTheListEmpty() {
        planet.getPlanetType().clear();
        assertTrue(planet.getOriginalPlanetType() != null
                && !planet.getOriginalPlanetType().isBlank());

        TileLayers.of(game, null, DisplayType.traits).draw(tile, TileStep.Traits);

        assertEquals(List.of(), planet.getPlanetType());
    }
}
