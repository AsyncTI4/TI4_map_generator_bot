package ti4.draft.items;

import ti4.draft.DraftItem;

public class SpeakerOrderDraftItem extends DraftItem {
    public SpeakerOrderDraftItem(String itemId) {
        super(Category.DRAFTORDER, itemId);
    }

    @Override
    public String getShortDescription() {
        return null;
    }

    @Override
    public String getLongDescription() {
        return null;
    }

    @Override
    public String getItemEmoji() {
        return null;
    }
}
