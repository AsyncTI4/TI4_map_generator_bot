package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.helpers.Emojis;

public class SpeakerOrderDraftItem extends DraftItem {
    public SpeakerOrderDraftItem(String itemId) {
        super(Category.DRAFTORDER, itemId);
    }

    @Override
    public String getShortDescription() {
        return "Table Position " + ItemId;
    }

    @Override
    public String getLongDescriptionImpl() {
        if ("1".equals(ItemId)) {
            return "Speaker Token + Table Position 1";
        }
        return "Table Position " + ItemId;
    }

    @Override
    public String getItemEmoji() {
        if ("1".equals(ItemId)) {
            return Emojis.SpeakerToken;
        }
        return Emojis.getResourceEmoji(Integer.parseInt(ItemId));
    }
}
