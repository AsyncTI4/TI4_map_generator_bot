package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;

import java.util.ArrayList;
import java.util.List;

public class CommoditiesDraftItem extends DraftItem {
    public CommoditiesDraftItem(String itemId) {
        super(Category.COMMODITIES, itemId);
    }

    private FactionModel getFaction() {
        if ("keleres".equals(ItemId)) {
            return Mapper.getFaction("keleresa");
        }
        return Mapper.getFaction(ItemId);
    }
    @Override
    public String getShortDescription() {
        return getFaction().getFactionName() + " Commodities";
    }

    @Override
    public String getLongDescriptionImpl() {
        int comms = getFaction().getCommodities();
        return comms + " Commodities";
    }

    @Override
    public String getItemEmoji() {
        return Emojis.comm;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(DraftItem.Generate(Category.COMMODITIES, faction.getAlias()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.COMMODITIES);
        return allItems;
    }
}
