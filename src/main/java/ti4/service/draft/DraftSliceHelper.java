package ti4.service.draft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import lombok.experimental.UtilityClass;
import ti4.helpers.AliasHandler;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class DraftSliceHelper {
    public static List<MiltyDraftSlice> parseSlicesFromString(String str) {
        int sliceIndex = 1;
        StringTokenizer sliceTokenizer = new StringTokenizer(str, ";");
        List<MiltyDraftSlice> slices = new ArrayList<>();
        while (sliceTokenizer.hasMoreTokens()) {
            slices.add(parseSliceFromString(sliceTokenizer.nextToken(), sliceIndex));
            sliceIndex++;
        }
        return slices;
    }

    private static MiltyDraftSlice parseSliceFromString(String str, int index) {
        List<String> tiles = Arrays.asList(str.split(","));
        List<MiltyDraftTile> draftTiles = tiles.stream()
                .map(AliasHandler::resolveTile)
                .map(DraftTileManager::findTile)
                .toList();
        MiltyDraftSlice slice = new MiltyDraftSlice();
        slice.setTiles(draftTiles);
        slice.setName(Character.toString(index - 1 + 'A'));
        return slice;
    }
}
