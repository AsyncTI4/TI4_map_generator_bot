package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.TI4Emoji;

public class AgentDraftItem extends DraftItem {
    private static final Pattern FIN_SEP = Pattern.compile("finSep");

    public AgentDraftItem(String itemId) {
        super(Category.AGENT, itemId);
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
        return "Agent - " + leader.getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText();
        }
        return "";
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return LeaderEmojis.getLeaderEmoji(leader.getID());
        }
        return null;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.AGENT);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, LeaderModel> allLeaders = Mapper.getLeaders();
        for (FactionModel faction : factions) {
            List<String> agents = faction.getLeaders();
            agents.removeIf(
                    (String leader) -> !"agent".equals(allLeaders.get(leader).getType()));
            for (String agent : agents) {
                allItems.add(generate(Category.AGENT, agent));
            }
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.AGENT);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, LeaderModel> allLeaders = Mapper.getLeaders();
        String[] results = FIN_SEP.split(game.getStoredValue("bannedLeaders"));
        for (FactionModel faction : factions) {
            List<String> agents = faction.getLeaders();
            agents.removeIf(
                    (String leader) -> !"agent".equals(allLeaders.get(leader).getType()));
            for (String agent : agents) {
                if (Arrays.asList(results).contains(agent)) {
                    continue;
                }
                allItems.add(generate(Category.AGENT, agent));
            }
        }
        return allItems;
    }
}
