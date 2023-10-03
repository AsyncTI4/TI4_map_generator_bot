package ti4.draft.items;

import ti4.draft.DraftItem;

public class TechDraftItem extends DraftItem {
    public TechDraftItem(String itemId) {
        super(Category.TECH, itemId);
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
