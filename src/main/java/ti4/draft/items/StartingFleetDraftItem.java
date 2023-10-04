package ti4.draft.items;

import org.apache.commons.lang3.StringUtils;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.FactionModel;

public class StartingFleetDraftItem extends DraftItem {
    public StartingFleetDraftItem(String itemId) {
        super(Category.STARTINGFLEET, itemId);
    }


    private FactionModel getFaction() {
        if (ItemId.equals("keleres")) {
            return Mapper.getFactionSetup("keleresa");
        }
        return Mapper.getFactionSetup(ItemId);
    }

    @Override
    public String getShortDescription() {
        return getFaction().getFactionName() + " Starting Fleet";
    }

    @Override
    public String getLongDescription() {
        String[] fleetDesc = getFaction().getStartingFleet().split(",");
        StringBuilder sb = new StringBuilder();
        for (String desc: fleetDesc) {
            String[] split = desc.split(" ");
            if (StringUtils.isNumeric(split[0])) {
                sb.append(split[0]).append(" ");
                sb.append(Mapper.getUnit(split[1]).getName());
            }
            else {
                sb.append(Mapper.getUnit(split[0]).getName());
            }
            sb.append(", ");
        }
        return sb.toString();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.NonUnitTechSkip;
    }
}
