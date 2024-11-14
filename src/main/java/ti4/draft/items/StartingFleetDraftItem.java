package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;

public class StartingFleetDraftItem extends DraftItem {
    public StartingFleetDraftItem(String itemId) {
        super(Category.STARTINGFLEET, itemId);
    }

    @JsonIgnore
    private FactionModel getFaction() {
        return Mapper.getFaction(ItemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getFaction().getFactionName() + " Starting Fleet";
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        return Helper.getUnitListEmojis(getFaction().getStartingFleet());
    }

    @JsonIgnore
    @Override
    public String getItemEmoji() {
        return Emojis.NonUnitTechSkip;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, Category.STARTINGFLEET);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(DraftItem.Generate(Category.STARTINGFLEET, faction.getAlias()));
        }
        return allItems;
    }
}
