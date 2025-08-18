package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ti4.draft.DraftItem;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.TI4Emoji;

public class TechDraftItem extends DraftItem {

    public TechDraftItem(String itemId) {
        super(Category.TECH, itemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getTech().getName();
    }

    private TechnologyModel getTech() {
        return Mapper.getTech(ItemId);
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        return getTech().getText() + " " + getTech().getRequirementsEmoji();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return getTech().getSingleTechEmoji();
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.TECH);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            for (var tech : faction.getFactionTech()) {
                allItems.add(generate(DraftItem.Category.TECH, tech));
            }
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.TECH);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedTechs"));
        for (FactionModel faction : factions) {
            for (var tech : faction.getFactionTech()) {
                if (Arrays.asList(results).contains(tech)) {
                    continue;
                }
                allItems.add(generate(DraftItem.Category.TECH, tech));
            }
        }
        return allItems;
    }
}
