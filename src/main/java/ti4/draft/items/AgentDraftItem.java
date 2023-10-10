package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Helper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

public class AgentDraftItem extends DraftItem {
    public AgentDraftItem(String itemId) {
        super(Category.AGENT, itemId);
    }

    private FactionModel getFaction() {
        if (ItemId.equals("keleres")) {
            return Mapper.getFactionSetup("keleresa");
        }
        return Mapper.getFactionSetup(ItemId);
    }

    private LeaderModel getLeader() {
        return Mapper.getLeader(getFaction().getLeaders().get(0));
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
    public String getLongDescriptionImpl() {
        return "*" + getLeader().getAbilityWindow() +"* " + getLeader().getAbilityText();
    }

    @Override
    public String getItemEmoji() {
        return Helper.getEmojiFromDiscord(getLeader().getID());
    }
}
