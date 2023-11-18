package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

import java.util.List;

public class CommanderDraftItem extends DraftItem {
    public CommanderDraftItem(String itemId) {
        super(Category.COMMANDER, itemId);
    }

    private FactionModel getFaction() {
        if (ItemId.equals("keleres")) {
            return Mapper.getFaction("keleresa");
        }
        return Mapper.getFaction(ItemId);
    }

    private LeaderModel getLeader() {
        List<String> leaders = getFaction().getLeaders();
        for (String leader : leaders) {
            if (leader.contains("commander")) {
                return Mapper.getLeader(leader);
            }
        }

        return null;
    }

    @Override
    public String getShortDescription() {
        LeaderModel leader = getLeader();
        if (leader == null)
        {
            return getAlias();
        }return "Commander - " + getLeader().getName();
    }

    @Override
    public String getLongDescriptionImpl() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText() + " **Unlock:** " + leader.getUnlockCondition();
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
