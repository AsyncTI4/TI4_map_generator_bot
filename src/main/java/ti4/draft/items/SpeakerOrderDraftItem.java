package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;

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
        if (ItemId.equals("1")) {
            return "Speaker Token + Table Position 1";
        }
        return "Table Position " + ItemId;
    }

    @Override
    public String getItemEmoji() {
        if (ItemId.equals("1")) {
            return Emojis.SpeakerToken;
        }
        return Emojis.getResourceEmoji(Integer.parseInt(ItemId));
    }
}
