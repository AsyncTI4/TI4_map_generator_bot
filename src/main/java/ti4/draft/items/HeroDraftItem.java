package ti4.draft.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

public class HeroDraftItem extends DraftItem {
    public HeroDraftItem(String itemId) {
        super(Category.HERO, itemId);
    }

    private LeaderModel getLeader() {
        return Mapper.getLeader(ItemId);
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


    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        HashMap<String, LeaderModel> allLeaders = Mapper.getLeaders();
        for (FactionModel faction : factions) {
            List<String> leaders = faction.getLeaders();
            leaders.removeIf((String leader) -> !"hero".equals(allLeaders.get(leader).getType()));
            if (leaders.isEmpty()) {
                continue;
            }
            allItems.add(DraftItem.Generate(Category.HERO, leaders.get(0)));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, Category.HERO);
        return allItems;
    }
}
