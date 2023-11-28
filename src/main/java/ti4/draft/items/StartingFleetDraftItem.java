package ti4.draft.items;

import org.apache.commons.lang3.StringUtils;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
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
        String[] fleetDesc = getFaction().getStartingFleet().split(",");
        StringBuilder sb = new StringBuilder();
        for (String desc: fleetDesc) {
            String[] split = desc.trim().split(" ");
            String alias;
            int count;
            if (StringUtils.isNumeric(split[0])) {
                count = Integer.parseInt(split[0]);
                alias = split[1];
            } else {
                count = 1;
                alias = split[0];
            }

            for (int i = 1; i <= count; i++) {
                sb.append(Emojis.getEmojiFromDiscord(Mapper.getUnitBaseTypeFromAsyncID(AliasHandler.resolveUnit(alias))));
            }
        }
        return sb.toString();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.NonUnitTechSkip;
    }
}
