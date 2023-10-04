package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Helper;
import ti4.model.LeaderModel;

public class CommanderDraftItem extends DraftItem {
    public CommanderDraftItem(String itemId) {
        super(Category.COMMANDER, itemId);
    }

    private LeaderModel getLeader() {
        if (ItemId.contains("commander")) {
            return Mapper.getLeader(ItemId);
        }
        return Mapper.getLeader(ItemId+"commander");
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
    public String getLongDescription() {
        return "*" + getLeader().getAbilityWindow() +"* " + getLeader().getAbilityText() + " **Unlock:** " + getLeader().getUnlockCondition();
    }

    @Override
    public String getItemEmoji() {
        return Helper.getEmojiFromDiscord(getLeader().getID());
    }
}
