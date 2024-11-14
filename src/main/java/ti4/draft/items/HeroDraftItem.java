package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    private LeaderModel getLeader() {
        return Mapper.getLeader(ItemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        LeaderModel leader = getLeader();
        if (leader == null) {
            return getAlias();
        }
        return "Hero - " + leader.getName().replace("\n", "");
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return "**" + leader.getAbilityName().orElse("").replace("\n", "") + "** - " + "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText();
        }
        return "";
    }

    @JsonIgnore
    @Override
    public String getItemEmoji() {

        LeaderModel leader = getLeader();
        if (leader != null) {
            return Emojis.getEmojiFromDiscord(leader.getID());
        }
        return "";
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, Category.HERO);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, LeaderModel> allLeaders = Mapper.getLeaders();
        for (FactionModel faction : factions) {
            List<String> leaders = faction.getLeaders();
            leaders.removeIf((String leader) -> !"hero".equals(allLeaders.get(leader).getType()));
            for (String leader : leaders) {
                allItems.add(DraftItem.generate(Category.HERO, leader));
            }
        }
        return allItems;
    }
}
