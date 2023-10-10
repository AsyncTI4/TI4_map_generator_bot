package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Helper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

import java.util.List;

public class HeroDraftItem extends DraftItem {
    public HeroDraftItem(String itemId) {
        super(Category.HERO, itemId);
    }

    private FactionModel getFaction() {
        if (ItemId.equals("keleres")) {
            return Mapper.getFactionSetup("keleresa");
        }
        return Mapper.getFactionSetup(ItemId);
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
            return "**" + leader.getAbilityName().replace("\n", "") + "** - " + "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText();
        }
        return "";
    }

    @Override
    public String getItemEmoji() {

        LeaderModel leader = getLeader();
        if (leader != null) {
            return Helper.getEmojiFromDiscord(leader.getID());
        }
        return "";
    }
}
