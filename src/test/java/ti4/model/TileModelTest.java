package ti4.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.image.TileHelper;
import ti4.testUtils.BaseTi4Test;

class TileModelTest extends BaseTi4Test {
    @Test
    void testTiles() {
        for (TileModel model : TileHelper.getAllTileModels()) {
            assertTrue(model.isValid(), model.getAlias() + ": invalid");
            assertTrue(validatePlanetIDs(model), model.getAlias() + ": invalid Planet IDs: " + model.getPlanets());
        }
    }

    @Test
    void everyFractureBackedTileIsFlaggedAsFracture() {
        // isFracture is the runtime marker; the fracture card back is the data these tiles were authored with.
        // Keep the two in lockstep so a new fracture tile cannot silently miss the flag.
        for (TileModel model : TileHelper.getAllTileModels()) {
            if (model.getTileBack() == TileModel.TileBack.FRACTURE) {
                assertTrue(model.isFracture(), model.getAlias() + ": has the fracture back but not isFracture");
            }
        }
        assertTrue(TileHelper.getTileById("fracture4").isFracture());
        assertFalse(TileHelper.getTileById("18").isFracture(), "Mecatol Rex should not be fracture");
    }

    private boolean validatePlanetIDs(TileModel model) {
        if (model.getPlanets() == null) return true;
        for (String planetId : model.getPlanets()) {
            if (!TileHelper.isValidPlanet(planetId)) return false;
        }
        return true;
    }
}
