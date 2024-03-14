package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ti4.generator.TileHelper;
import ti4.testUtils.BaseTi4Test;

public class PlanetModelTest extends BaseTi4Test {
    @Test
    public void testPlanets() {
        for (PlanetModel model : TileHelper.getAllPlanets().values()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            // assertTrue(validateFaction(model), model.getAlias() + ": invalid FactionID");
            // assertTrue(validateHomebrewReplacesID(model), model.getAlias() + ": invalid HomebrewReplacesID");
        }
    }
}
