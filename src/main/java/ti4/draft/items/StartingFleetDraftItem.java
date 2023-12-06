package ti4.draft.items;

import org.apache.commons.lang3.StringUtils;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.FactionModel;

public class StartingFleetDraftItem extends DraftItem {
    public StartingFleetDraftItem(String itemId) {
        super(Category.STARTINGFLEET, itemId);
    }


    private FactionModel getFaction() {
        if ("keleres".equals(ItemId)) {
            return Mapper.getFaction("keleresa");
        }
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
}
