package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;

public class CommoditiesDraftItem extends DraftItem {
    public CommoditiesDraftItem(String itemId) {
        super(Category.COMMODITIES, itemId);
    }

    @Override
    public String getShortDescription() {
        return Mapper.getFactionRepresentations().get(ItemId) + " Commodities";
    }

    @Override
    public String getLongDescription() {
        int comms = Mapper.getFactionSetup(ItemId).getCommodities();
        return comms + " Commodities";
    }

    @Override
    public String getItemEmoji() {
        return Emojis.comm;
    }
}
