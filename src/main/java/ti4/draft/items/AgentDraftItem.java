package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Helper;
import ti4.model.LeaderModel;

public class AgentDraftItem extends DraftItem {
    public AgentDraftItem(String itemId) {
        super(Category.AGENT, itemId);
    }
    private LeaderModel getLeader() {
        if (ItemId.contains("agent")) {
            return Mapper.getLeader(ItemId);
        }
        return Mapper.getLeader(ItemId+"agent");
    }

    @Override
    public String getShortDescription() {
        LeaderModel leader = getLeader();
        if (leader == null)
        {
            return getAlias();
        }
        return "Agent - " + leader.getName();
    }

    @Override
    public String getLongDescription() {
        return "*" + getLeader().getAbilityWindow() +"* " + getLeader().getAbilityText();
    }

    @Override
    public String getItemEmoji() {
        return Helper.getEmojiFromDiscord(getLeader().getID());
    }
}
