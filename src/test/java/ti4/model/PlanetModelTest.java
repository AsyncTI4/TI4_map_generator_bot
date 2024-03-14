package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.testUtils.BaseTi4Test;

public class PlanetModelTest extends BaseTi4Test {
    @Test
    public void testPlanets() {
        for (PlanetModel model : TileHelper.getAllPlanets().values()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validateTileId(model), model.getAlias() + ": invalid TileID: " + model.getTileId());
            assertTrue(validateFactionHomeworld(model), model.getAlias() + ": invalid Faction Homeworld: " + model.getTileId());
        }
    }

    private boolean validateTileId(PlanetModel model) {
        if (model.getTileId() == null) return true;
        return TileHelper.getAllTiles().containsKey(model.getTileId());
    }

    private boolean validateFactionHomeworld(PlanetModel model) {
        if (model.getFactionHomeworld() == null) return true;
        return Mapper.isValidFaction(model.getFactionHomeworld());
    }
}
