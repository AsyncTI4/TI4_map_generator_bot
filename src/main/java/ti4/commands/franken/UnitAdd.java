package ti4.commands.franken;

import java.util.List;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class UnitAdd extends UnitAddRemove {
    public UnitAdd() {
        super(Constants.UNIT_ADD, "Add a unit to your faction");
    }

    @Override
    public void doAction(Player player, List<String> unitIDs) {
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, getActiveGame())).append(" added units:\n");
        for (String unitID : unitIDs ){
            if (player.ownsUnit(unitID)) {
                sb.append("> ").append(unitID).append(" (player had this unit)");
            } else {
                sb.append("> ").append(unitID);
            }
            sb.append("\n");
            player.addOwnedUnitByID(unitID);
        }
        sendMessage(sb.toString());
    }
}
