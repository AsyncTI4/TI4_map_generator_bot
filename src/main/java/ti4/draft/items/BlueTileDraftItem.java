package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.helpers.PatternHelper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyDraftTile;

public class BlueTileDraftItem extends TileDraftItem {

    public BlueTileDraftItem(String itemId) {
        super(DraftCategory.BLUETILE, itemId);
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return PlanetEmojis.SemLor;
    }

    public static List<DraftItem> buildAllDraftableItems(MiltyDraftManager draftManager) {
        List<DraftItem> allItems = new ArrayList<>();
        for (MiltyDraftTile tile : draftManager.getBlue()) {
            allItems.add(generate(DraftCategory.BLUETILE, tile.getTile().getTileID()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.BLUETILE);
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(MiltyDraftManager draftManager, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedTiles"));
        for (MiltyDraftTile tile : draftManager.getBlue()) {
            if (Arrays.asList(results).contains(tile.getTile().getTileID())) {
                continue;
            }
            allItems.add(generate(DraftCategory.BLUETILE, tile.getTile().getTileID()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.BLUETILE);
        return allItems;
    }
}
