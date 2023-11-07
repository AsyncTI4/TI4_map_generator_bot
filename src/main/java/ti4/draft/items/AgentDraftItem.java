package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
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
        FactionModel faction = getFaction();
        if (faction != null) {
            return Mapper.getLeader(faction.getLeaders().get(0));
        }
        return null;
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
        LeaderModel leader = getLeader();
        if (leader != null) {
            return "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText();
        }
        return "";
    }

    @Override
    public String getItemEmoji() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return Emojis.getEmojiFromDiscord(leader.getID());
        }
        return "";
    }
}
