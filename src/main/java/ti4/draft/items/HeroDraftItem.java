package ti4.draft.items;

import java.util.List;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

public class HeroDraftItem extends DraftItem {
    public HeroDraftItem(String itemId) {
        super(Category.HERO, itemId);
    }

    private FactionModel getFaction() {
        if ("keleres".equals(ItemId)) {
            return Mapper.getFaction("keleresa");
        }
        return Mapper.getFaction(ItemId);
    }

    private LeaderModel getLeader() {
        FactionModel faction = getFaction();
        if (faction == null) {
            return Mapper.getLeader(ItemId);
        }
        List<String> leaders = faction.getLeaders();
        for (String leader : leaders) {
            if (leader.contains("hero")) {
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
        }
        return "Hero - " + leader.getName().replace("\n", "");
    }

    @Override
    public String getLongDescriptionImpl() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return "**" + leader.getAbilityName().orElse("").replace("\n", "") + "** - " + "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText();
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
