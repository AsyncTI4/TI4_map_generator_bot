package ti4.service.franken;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.draft.DraftCategory;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class FrankenUnitService {

    public static void addUnits(
            GenericInteractionCreateEvent event, Player player, List<String> unitIDs, boolean dupes) {
        if (player.getGame().isVeiledHeartMode()) {
            String msg = "Added a veiled card. Refresh your `#cards-info` thread to find a button to reveal it";
            MessageHelper.sendEphemeralMessageToEventChannel(event, msg);

            String key = "veiledCards" + player.getFaction();
            String val = player.getGame().getStoredValue(key);
            val += "_" + String.join("_", unitIDs);
            player.getGame().setStoredValue(key, val + "_");
            return;
        }

        StringBuilder sb = new StringBuilder(player.toString()).append(" added units:\n");
        for (String unitID : unitIDs) {
            UnitModel unitModel = Mapper.getUnit(unitID);
            if (player.getGame().isTwilightsFallMode()
                    && ("fs".equalsIgnoreCase(unitModel.getAsyncId()) || "mf".equalsIgnoreCase(unitModel.getAsyncId()))
                    && !unitID.contains("_")) {
                dupes = true;
            }
            if (player.ownsUnit(unitID)) {
                sb.append("> ").append(unitID).append(" (player had this unit)");
            } else {
                if (!dupes) {
                    UnitModel oldBaseType;
                    while ((oldBaseType = player.getUnitByBaseType(unitModel.getBaseType())) != null) {
                        player.removeOwnedUnitByID(oldBaseType.getAlias());
                    }
                }
                String unitText = unitID;
                DraftCategory category = FrankenAlternateTextService.getUnitCategory(unitID);
                if (category != null) {
                    unitText = FrankenAlternateTextService.getRepresentationWithAlternateText(
                            player.getGame(),
                            category,
                            unitID,
                            unitModel.getNameRepresentation(),
                            unitModel.getUnitRepresentation());
                }
                sb.append("> ").append(unitText);
                player.addOwnedUnitByID(unitID);
            }
            if ("naaz_mech".equalsIgnoreCase(unitID)) {
                player.addOwnedUnitByID("naaz_mech_space");
                sb.append("> naaz_mech_space");
            }
            sb.append('\n');
        }
        MessageHelper.sendEphemeralMessageToEventChannel(event, sb.toString());
    }

    public static void removeUnits(GenericInteractionCreateEvent event, Player player, List<String> unitIDs) {
        StringBuilder sb = new StringBuilder(player.toString()).append(" removed units:\n");
        for (String unitID : unitIDs) {
            if (!player.ownsUnit(unitID)) {
                sb.append("> ").append(unitID).append(" (player did not have this unit)");
            } else {
                sb.append("> ").append(unitID);
            }
            sb.append('\n');
            player.removeOwnedUnitByID(unitID);
            UnitModel u = Mapper.getUnit(unitID);
            if (u.getUnitType() != UnitType.Flagship && u.getUnitType() != UnitType.Mech) {
                String replacementUnit = u.getBaseType();
                player.addOwnedUnitByID(replacementUnit);
            }

            if ("naaz_mech".equalsIgnoreCase(unitID)) {
                player.removeOwnedUnitByID("naaz_mech_space");
            }
        }
        MessageHelper.sendEphemeralMessageToEventChannel(event, sb.toString());
    }
}
