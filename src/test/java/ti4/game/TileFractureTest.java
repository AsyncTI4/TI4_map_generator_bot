package ti4.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.testUtils.BaseTi4Test;

class TileFractureTest extends BaseTi4Test {

    @Test
    void fractureTilesAreFractureWhereverTheySit() {
        assertTrue(new Tile("fracture4", "frac4").isFracture());
        assertTrue(new Tile("fracture4", "305").isFracture(), "the flag should not depend on the board slot");
        assertFalse(new Tile("18", "000").isFracture());
    }

    @Test
    void legacyFracPositionsAreStillFracture() {
        // Games predating the isFracture flag identified Fracture space by the frac1-frac7 slots alone
        assertTrue(new Tile("18", "frac3").isFracture());
    }

    @Test
    void unknownTileIdsDoNotBlowUp() {
        // getTileModel() is null for tile IDs that are no longer in the registry - see Tile.isValid()
        Tile unknown = new Tile("no_such_tile_id", "305");
        assertFalse(unknown.isFracture());
        assertFalse(unknown.hasEgress());
    }

    @Test
    void aFractureTokenMakesAnyTileFracture() {
        Tile tile = new Tile("18", "000");
        tile.addToken(Constants.TOKEN_FRACTURE, Constants.SPACE);
        assertTrue(tile.isFracture());
    }

    @Test
    void egressTilesAreDetected() {
        assertTrue(new Tile("fracture2", "frac2").hasEgress());
        assertTrue(new Tile("fracture6", "frac6").hasEgress());
        assertFalse(new Tile("fracture3", "frac3").hasEgress());
    }
}
