package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.BreakthroughModel;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.service.emoji.TI4Emoji;

public class BreakthroughDraftItem extends DraftItem {

    public BreakthroughDraftItem(String itemId) {
        super(DraftCategory.BREAKTHROUGH, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getBreakthroughModel().getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getBreakthroughModel().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        BreakthroughModel breakthroughModel = getBreakthroughModel();
        StringBuilder sb = new StringBuilder();
        sb.append(breakthroughModel.getText()).append("\n");
        return sb.toString();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return getBreakthroughModel().getFactionEmoji();
    }

    @JsonIgnore
    private BreakthroughModel getBreakthroughModel() {
        if (!Mapper.isValidBreakthrough(getItemId())) System.err.println(getItemId() + " is not a valid breakthrough");
        return Mapper.getBreakthrough(getItemId());
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.BREAKTHROUGH);
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.BREAKTHROUGH);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedBreakthroughs"));
            String bt = faction.getBreakthrough();
            if (Arrays.asList(results).contains(bt)) {
                continue;
            }
            allItems.add(generate(DraftCategory.BREAKTHROUGH, bt));
        }
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(generate(DraftCategory.BREAKTHROUGH, faction.getBreakthrough()));
        }
        return allItems;
    }
}
