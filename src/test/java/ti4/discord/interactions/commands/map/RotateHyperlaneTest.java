package ti4.discord.interactions.commands.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ti4.model.TileHelper;
import ti4.testUtils.BaseTi4Test;

class RotateHyperlaneTest extends BaseTi4Test {

    @Test
    void rotatesDegreeSuffixedHyperlanes() {
        assertEquals("83a60", RotateHyperlane.rotatedTileId("83a", 60));
        assertEquals("83a120", RotateHyperlane.rotatedTileId("83a60", 60));
        assertEquals("83a", RotateHyperlane.rotatedTileId("83a300", 60));
        assertEquals("83a300", RotateHyperlane.rotatedTileId("83a", -60));
        assertEquals("83a", RotateHyperlane.rotatedTileId("83a", 360));
    }

    @Test
    void rotatesStepNumberedHyperlanes() {
        assertEquals("hl_frost_3", RotateHyperlane.rotatedTileId("hl_frost_2", 60));
        assertEquals("hl_frost_0", RotateHyperlane.rotatedTileId("hl_frost_5", 60));
        assertEquals("hl_frost_5", RotateHyperlane.rotatedTileId("hl_frost_0", -60));
        assertEquals("hl_frost_2", RotateHyperlane.rotatedTileId("hl_frost_2", 360));
    }

    @Test
    void symmetricHyperlanesWrapAtTheirVariantCount() {
        assertEquals("hl_frostcross_0", RotateHyperlane.rotatedTileId("hl_frostcross_2", 60));
        assertEquals("hl_riftabout3_0", RotateHyperlane.rotatedTileId("hl_riftabout3_1", 60));
        assertEquals("hl_spaghet_0", RotateHyperlane.rotatedTileId("hl_spaghet_0", 60));
    }

    @ParameterizedTest
    @CsvSource({
        "hl_4squeeze, 3",
        "hl_bball, 3",
        "hl_crossed, 6",
        "hl_flair, 1",
        "hl_frost, 6",
        "hl_frostcross, 3",
        "hl_horizon, 6",
        "hl_mergeleft, 3",
        "hl_mergeright, 3",
        "hl_no, 3",
        "hl_oops, 1",
        "hl_riftabout1, 6",
        "hl_riftabout2, 3",
        "hl_riftabout3, 2",
        "hl_riftabout6, 1",
        "hl_roundabout1, 6",
        "hl_roundabout2, 3",
        "hl_roundabout3, 2",
        "hl_roundabout6, 1",
        "hl_spaghet, 1"
    })
    void rotatesEveryStepNumberedHyperlaneFamily(String base, int variants) {
        for (int orientation = 0; orientation < variants; orientation++) {
            String id = base + "_" + orientation;
            assertEquals(id, TileHelper.getTileById(id).getId());
            for (int rotation = -360; rotation <= 360; rotation += 60) {
                String expected = base + "_" + Math.floorMod(orientation + rotation / 60, variants);
                assertEquals(expected, RotateHyperlane.rotatedTileId(id, rotation), id + " rotated by " + rotation);
            }
        }
    }

    @Test
    void nonHyperlaneTilesCannotBeRotated() {
        assertNull(RotateHyperlane.rotatedTileId("18", 60));
        assertNull(RotateHyperlane.rotatedTileId("hl", 60));
        assertNull(RotateHyperlane.rotatedTileId("hl_nosuchlane_0", 60));
    }
}
