package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.model.DraftErrataModel;

public class SpeakerOrderDraftItem extends DraftItem {
    public SpeakerOrderDraftItem(String itemId) {
        super(Category.DRAFTORDER, itemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return "Table Position " + ItemId;
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        if ("1".equals(ItemId)) {
            return "Speaker Token + Table Position 1";
        }
        return "Table Position " + ItemId;
    }

    @JsonIgnore
    @Override
    public String getItemEmoji() {
        try {
            return Emojis.getSpeakerPickEmoji(getSpeakerOrder());
        } catch (Exception e) {
            return Emojis.getResourceEmoji(getSpeakerOrder());
        }
    }

    @JsonIgnore
    public int getSpeakerOrder() {
        return Integer.parseInt(ItemId);
    }

    public static List<DraftItem> buildAllDraftableItems(Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        for (int i = 0; i < game.getRealPlayers().size(); i++) {
            allItems.add(DraftItem.generate(DraftItem.Category.DRAFTORDER, Integer.toString(i + 1)));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.DRAFTORDER);
        return allItems;
    }
}
