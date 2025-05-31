package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
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
                allItems.add(DraftItem.generate(DraftItem.Category.TECH, tech));
            }
        }
        return allItems;
    }
}
