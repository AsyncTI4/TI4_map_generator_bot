package ti4.model;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlanetModelTest extends BaseTi4Test {
    @Test
    public void testPlanets() {
        for (PlanetModel model : TileHelper.getAllPlanetModels()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validateTileId(model), model.getAlias() + ": invalid TileID: " + model.getTileId());
            assertTrue(validateTileContainsPlanet(model), model.getAlias() + ": invalid TileID - tile does not contain planet: " + model.getTileId());
            assertTrue(validateFactionHomeworld(model), model.getAlias() + ": invalid Faction Homeworld: " + model.getTileId());
        }
    }

    private boolean validateTileId(PlanetModel model) {
        if (model.getTileId() == null) return true;
        return TileHelper.isValidTile(model.getTileId());
    }

    private boolean validateTileContainsPlanet(PlanetModel model) {
        if (model.getTileId() == null) return true;
        return TileHelper.getTileById(model.getTileId()).getPlanets().contains(model.getAlias());
    }

    private boolean validateFactionHomeworld(PlanetModel model) {
        if (model.getFactionHomeworld() == null) return true;
        return Mapper.isValidFaction(model.getFactionHomeworld());
    }
}
