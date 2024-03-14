package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ti4.generator.TileHelper;
import ti4.testUtils.BaseTi4Test;

public class TileModelTest extends BaseTi4Test {
    @Test
    public void testTiles() {
        for (TileModel model : TileHelper.getAllTiles().values()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validatePlanetIDs(model), model.getAlias() + ": invalid Planet IDs: " + model.getPlanetIds());
        }
    }

    private boolean validatePlanetIDs(TileModel model) {
        if (model.getPlanetIds() == null) return true;
        for (String planetId : model.getPlanetIds()) {
            if (!TileHelper.getAllPlanets().containsKey(planetId)) return false;
        }
        return true;
    }
}
