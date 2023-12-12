package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class AgentDraftItem extends DraftItem {
    public AgentDraftItem(String itemId) {
        super(Category.AGENT, itemId);
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

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        HashMap<String, LeaderModel> allLeaders = Mapper.getLeaders();
        for (FactionModel faction : factions) {
            List<String> agents = faction.getLeaders();
            agents.removeIf((String leader) -> {
               return !"agent".equals(allLeaders.get(leader).getType());
            });
            if (agents.isEmpty()) {
                continue;
            }
            allItems.add(DraftItem.Generate(Category.AGENT, agents.get(0)));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.AGENT);
        return allItems;
    }
}
