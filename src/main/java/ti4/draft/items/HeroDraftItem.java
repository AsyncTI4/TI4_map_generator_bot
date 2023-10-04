package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Helper;
import ti4.model.LeaderModel;

public class HeroDraftItem extends DraftItem {
    public HeroDraftItem(String itemId) {
        super(Category.HERO, itemId);
    }
    private LeaderModel getLeader() {
        if (ItemId.contains("hero")) {
            return Mapper.getLeader(ItemId);
        }
        return Mapper.getLeader(ItemId+"hero");
    }

    @Override
    public String getShortDescription() {
        LeaderModel leader = getLeader();
        if (leader == null)
        {
            return getAlias();
        }
        return "Hero - " + leader.getName();
    }

    @Override
    public String getLongDescription() {
        return "**" + getLeader().getAbilityName() + "** - " + "*" + getLeader().getAbilityWindow() +"* " + getLeader().getAbilityText();
    }

    @Override
    public String getItemEmoji() {
        return Helper.getEmojiFromDiscord(getLeader().getID());
    }
}
