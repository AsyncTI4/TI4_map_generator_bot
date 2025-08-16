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
import ti4.service.emoji.TI4Emoji;

public class HeroDraftItem extends DraftItem {
    private static final Pattern FIN_SEP = Pattern.compile("finSep");

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
            return "**" + leader.getAbilityName().orElse("").replace("\n", "") + "** - " + "*"
                    + leader.getAbilityWindow() + "* " + leader.getAbilityText();
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
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, Category.HERO);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, LeaderModel> allLeaders = Mapper.getLeaders();
        for (FactionModel faction : factions) {
            List<String> leaders = faction.getLeaders();
            leaders.removeIf(
                    (String leader) -> !"hero".equals(allLeaders.get(leader).getType()));
            for (String leader : leaders) {
                allItems.add(generate(Category.HERO, leader));
            }
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, Category.HERO);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, LeaderModel> allLeaders = Mapper.getLeaders();
        String[] results = FIN_SEP.split(game.getStoredValue("bannedLeaders"));
        for (FactionModel faction : factions) {
            List<String> leaders = faction.getLeaders();
            leaders.removeIf(
                    (String leader) -> !"hero".equals(allLeaders.get(leader).getType()));
            for (String leader : leaders) {
                if (Arrays.asList(results).contains(leader)) {
                    continue;
                }
                allItems.add(generate(Category.HERO, leader));
            }
        }
        return allItems;
    }
}
