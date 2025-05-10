package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;

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

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getFaction().getFactionName() + " Commodities";
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        int comms = getFaction().getCommodities();
        return comms + " Commodities";
    }

    @JsonIgnore
    public int getCommodities() {
        return getFaction().getCommodities();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return MiscEmojis.comm;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.COMMODITIES);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(DraftItem.generate(Category.COMMODITIES, faction.getAlias()));
        }
        return allItems;
    }
}
