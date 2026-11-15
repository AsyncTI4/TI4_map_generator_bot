package ti4.service.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Tile;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.Mapper;
import ti4.service.milty.MiltyDraftSlice;
import ti4.testUtils.BaseTi4Test;

class NucleusImageGeneratorServiceTest extends BaseTi4Test {

    @Test
    void nucleusPositionWithoutTileIsSkippedInsteadOfThrowing() {
        MapTemplateModel template = Mapper.getMapTemplates().stream()
                .filter(MapTemplateModel::isNucleusTemplate)
                .findFirst()
                .orElseThrow();
        Map<String, Tile> tiles = new HashMap<>();
        boolean skippedFirst = false;
        int expectedSliceEntries = 0;
        for (MapTemplateTile templateTile : template.getTemplateTiles()) {
            if (templateTile.getNucleusNumbers() == null
                    || templateTile.getNucleusNumbers().isEmpty()) continue;
            if (!skippedFirst) {
                skippedFirst = true;
                continue;
            }
            tiles.put(templateTile.getPos(), new Tile("19", templateTile.getPos()));
            expectedSliceEntries += templateTile.getNucleusNumbers().size();
        }

        Map<String, MiltyDraftSlice> slices = NucleusImageGeneratorService.getSeatToNucleusTiles(template, tiles);

        assertFalse(slices.isEmpty());
        assertEquals(
                expectedSliceEntries,
                slices.values().stream().mapToInt(s -> s.getTiles().size()).sum());
    }
}
