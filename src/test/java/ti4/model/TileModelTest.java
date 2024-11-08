package ti4.model;

import org.junit.jupiter.api.Test;
import ti4.generator.TileHelper;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TileModelTest extends BaseTi4Test {
    @Test
    public void testTiles() {
        for (TileModel model : TileHelper.getAllTileModels()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validatePlanetIDs(model), model.getAlias() + ": invalid Planet IDs: " + model.getPlanets());
        }
    }

    private boolean validatePlanetIDs(TileModel model) {
        if (model.getPlanets() == null) return true;
        for (String planetId : model.getPlanets()) {
            if (!TileHelper.getPlanetIdsToPlanetModels().containsKey(planetId)) return false;
        }
        return true;
    }
}
