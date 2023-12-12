package ti4.draft.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

public class CommanderDraftItem extends DraftItem {
    public CommanderDraftItem(String itemId) {
        super(Category.COMMANDER, itemId);
    }

    @JsonIgnore
    private LeaderModel getLeader() {
        return Mapper.getLeader(ItemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        LeaderModel leader = getLeader();
        if (leader == null)
        {
            return getAlias();
        }return "Commander - " + getLeader().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText() + " **Unlock:** " + leader.getUnlockCondition();
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
        List<DraftItem> allItems = new ArrayList<>();
        HashMap<String, LeaderModel> allLeaders = Mapper.getLeaders();
        for (FactionModel faction : factions) {
            List<String> leaders = faction.getLeaders();
            leaders.removeIf((String leader) -> !"commander".equals(allLeaders.get(leader).getType()));
            if (leaders.isEmpty()) {
                continue;
            }
            allItems.add(DraftItem.Generate(Category.COMMANDER, leaders.get(0)));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.COMMANDER);
        return allItems;
    }
}
