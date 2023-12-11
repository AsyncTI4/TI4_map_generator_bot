package ti4.draft.items;

import org.apache.commons.lang3.StringUtils;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;

import java.util.ArrayList;
import java.util.List;

public class StartingFleetDraftItem extends DraftItem {
    public StartingFleetDraftItem(String itemId) {
        super(Category.STARTINGFLEET, itemId);
    }


    private FactionModel getFaction() {
        return Mapper.getFaction(ItemId);
    }

    @Override
    public String getShortDescription() {
        return getFaction().getFactionName() + " Starting Fleet";
    }

    @Override
    public String getLongDescriptionImpl() {
        return Helper.getUnitListEmojis(getFaction().getStartingFleet());
    }

    @Override
    public String getItemEmoji() {
        return Emojis.NonUnitTechSkip;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            if ("keleresa".equals(faction.getAlias())){
                allItems.add(DraftItem.Generate(Category.STARTINGFLEET, "keleres"));
            } else {
                allItems.add(DraftItem.Generate(Category.STARTINGFLEET, faction.getAlias()));
            }
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, Category.STARTINGFLEET);
        return allItems;
    }
}
