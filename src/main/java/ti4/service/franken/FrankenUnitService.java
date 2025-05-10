package ti4.service.franken;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class FrankenUnitService {

    public static void addUnits(GenericInteractionCreateEvent event, Player player, List<String> unitIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added units:\n");
        for (String unitID : unitIDs) {
            UnitModel unitModel = Mapper.getUnit(unitID);

            if (player.ownsUnit(unitID)) {
                sb.append("> ").append(unitID).append(" (player had this unit)");
            } else {
                UnitModel oldBaseType;
                while ((oldBaseType = player.getUnitByBaseType(unitModel.getBaseType())) != null)
                    player.removeOwnedUnitByID(oldBaseType.getAlias());
                sb.append("> ").append(unitID);
                player.addOwnedUnitByID(unitID);
            }
            if (unitID.equalsIgnoreCase("naaz_mech")) {
                player.addOwnedUnitByID("naaz_mech_space");
                sb.append("> naaz_mech_space");
            }
            sb.append("\n");
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

    public static void removeUnits(GenericInteractionCreateEvent event, Player player, List<String> unitIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed units:\n");
        for (String unitID : unitIDs) {
            if (!player.ownsUnit(unitID)) {
                sb.append("> ").append(unitID).append(" (player did not have this unit)");
            } else {
                sb.append("> ").append(unitID);
            }
            sb.append("\n");
            player.removeOwnedUnitByID(unitID);

            if (unitID.equalsIgnoreCase("naaz_mech")) {
                player.removeOwnedUnitByID("naaz_mech_space");
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
