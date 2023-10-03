package ti4.draft.items;

import ti4.draft.DraftItem;

public class HeroDraftItem extends DraftItem {
    public HeroDraftItem(String itemId) {
        super(Category.HERO, itemId);
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
