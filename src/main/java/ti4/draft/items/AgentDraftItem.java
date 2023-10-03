package ti4.draft.items;

import ti4.draft.DraftItem;

public class AgentDraftItem extends DraftItem {
    public AgentDraftItem(String itemId) {
        super(Category.AGENT, itemId);
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
