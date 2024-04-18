package ti4.commands.franken;

import java.util.List;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class UnitAdd extends UnitAddRemove {
    public UnitAdd() {
        super(Constants.UNIT_ADD, "Add a unit to your faction");
    }

    @Override
    public void doAction(Player player, List<String> unitIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added units:\n");
        for (String unitID : unitIDs) {
            UnitModel unitModel = Mapper.getUnit(unitID);
            player.removeOwnedUnitByID(unitModel.getBaseType());
            if (player.ownsUnit(unitID)) {
                sb.append("> ").append(unitID).append(" (player had this unit)");
            } else {
                sb.append("> ").append(unitID);
            }
            sb.append("\n");
            player.addOwnedUnitByID(unitID);
            if (unitID.equalsIgnoreCase("naaz_mech")) {
                unitID.equalsIgnoreCase("naaz_mech_space");
            }
        }
        MessageHelper.sendMessageToEventChannel(getEvent(), sb.toString());
    }
}
