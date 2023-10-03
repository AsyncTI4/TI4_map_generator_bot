package ti4.draft.items;

import ti4.draft.DraftItem;

public class MechDraftItem extends DraftItem {
    public MechDraftItem(String itemId) {
        super(Category.MECH, itemId);
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
