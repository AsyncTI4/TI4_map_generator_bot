package ti4.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ti4.model.TileHelper;
import ti4.model.TileModel;
import ti4.testUtils.BaseTi4Test;

class TileRepresentationTest extends BaseTi4Test {

    // getRepresentation used to rebuild a map of every tile model's name on each call; it now looks the model up
    // directly and must still return exactly what that map held, for every tile and for unknown ids.
    @Test
    void matchesTheNameOfEveryTileModel() {
        Map<String, String> representations = TileHelper.getAllTileModels().stream()
                .collect(Collectors.toMap(TileModel::getId, TileModel::getNameNullSafe));
        for (String tileId : TileHelper.getAllTileIds()) {
            assertEquals(representations.get(tileId), new Tile(tileId, "000").getRepresentation(), tileId);
        }
    }

    @Test
    void unknownTileFallsBackToIdAndPosition() {
        assertEquals("nosuchtile(101)", new Tile("nosuchtile", "101").getRepresentation());
    }
}
