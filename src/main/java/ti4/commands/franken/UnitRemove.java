package ti4.commands.franken;

import java.util.List;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class UnitRemove extends UnitAddRemove {
    public UnitRemove() {
        super(Constants.UNIT_REMOVE, "Remove an unit from your faction");
    }

    @Override
    public void doAction(Player player, List<String> unitIDs) {
        StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, getActiveMap())).append(" removed units:\n");
        for (String unitID : unitIDs) {
            if (!player.ownsUnit(unitID)) {
                sb.append("> ").append(unitID).append(" (player did not have this unit)");
            } else {
                sb.append("> ").append(unitID);
            }
            sb.append("\n");
            player.removeOwnedUnitByID(unitID);
        }
        sendMessage(sb.toString());
    }
}
