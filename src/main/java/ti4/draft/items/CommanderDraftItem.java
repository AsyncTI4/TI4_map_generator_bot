package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import ti4.helpers.PatternHelper;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.service.emoji.TI4Emoji;

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
        if (leader == null) {
            return getAlias();
        }
        return "Commander - " + getLeader().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return "*" + leader.getAbilityWindow() + "* " + leader.getAbilityText() + " **Unlock:** "
                    + leader.getUnlockCondition();
        }
        return "";
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        LeaderModel leader = getLeader();
        if (leader != null) {
            return leader.getLeaderEmoji();
        }
        return null;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.COMMANDER);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, LeaderModel> allLeaders = Mapper.getLeaders();
        for (FactionModel faction : factions) {
            List<String> leaders = faction.getLeaders();
            leaders.removeIf((String leader) ->
                    !"commander".equals(allLeaders.get(leader).getType()));
            for (String leader : leaders) {
                allItems.add(generate(Category.COMMANDER, leader));
            }
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.COMMANDER);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, LeaderModel> allLeaders = Mapper.getLeaders();
        String[] results = PatternHelper.STORAGE_SEPARATOR_PATTERN.split(game.getStoredValue("bannedLeaders"));
        for (FactionModel faction : factions) {
            List<String> leaders = faction.getLeaders();
            leaders.removeIf((String leader) ->
                    !"commander".equals(allLeaders.get(leader).getType()));
            for (String leader : leaders) {
                if (Arrays.asList(results).contains(leader)) {
                    continue;
                }
                allItems.add(generate(Category.COMMANDER, leader));
            }
        }
        return allItems;
    }
}
