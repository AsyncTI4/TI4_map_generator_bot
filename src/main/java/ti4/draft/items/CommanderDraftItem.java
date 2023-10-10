package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
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
            return Mapper.getFactionSetup("keleresa");
        }
        return Mapper.getFactionSetup(ItemId);
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
        return "*" + getLeader().getAbilityWindow() +"* " + getLeader().getAbilityText() + " **Unlock:** " + getLeader().getUnlockCondition();
    }

    @Override
    public String getItemEmoji() {
        return Helper.getEmojiFromDiscord(getLeader().getID());
    }
}
