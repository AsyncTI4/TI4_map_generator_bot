package ti4.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.testUtils.BaseTi4Test;

class TileRepresentationTest extends BaseTi4Test {

    // getRepresentation used to rebuild Mapper.getTileRepresentations() on every call; it now looks the model up
    // directly and must still return exactly what that map held, for every tile and for unknown ids.
    @Test
    void matchesTheTileRepresentationMapForEveryTile() {
        Map<String, String> representations = Mapper.getTileRepresentations();
        for (String tileId : TileHelper.getAllTileIds()) {
            assertEquals(representations.get(tileId), new Tile(tileId, "000").getRepresentation(), tileId);
        }
    }

    @Test
    void unknownTileFallsBackToIdAndPosition() {
        assertEquals("nosuchtile(101)", new Tile("nosuchtile", "101").getRepresentation());
    }
}
