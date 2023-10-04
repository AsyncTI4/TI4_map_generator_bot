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
        var representations = Mapper.getUnitRepresentations();
        String[] fleetDesc = getFaction().getStartingFleet().split(",");
        StringBuilder sb = new StringBuilder();
        for (String desc: fleetDesc) {
            String[] split = desc.trim().split(" ");
            String alias;
            if (StringUtils.isNumeric(split[0])) {
                sb.append(split[0]).append(" ");
                alias = split[1];
            }
            else {
                alias = split[0];
            }

            sb.append(representations.get(AliasHandler.resolveUnit(alias)));
            sb.append(", ");
        }
        return sb.toString();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.NonUnitTechSkip;
    }
}
