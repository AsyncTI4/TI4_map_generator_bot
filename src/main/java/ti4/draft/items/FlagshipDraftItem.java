package ti4.draft.items;

import ti4.draft.DraftItem;

public class FlagshipDraftItem extends DraftItem {
    public FlagshipDraftItem(String itemId) {
        super(Category.FLAGSHIP, itemId);
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
