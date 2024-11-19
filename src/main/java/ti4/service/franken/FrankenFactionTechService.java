package ti4.service.franken;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

@UtilityClass
public class FrankenFactionTechService {

    public static void addFactionTechs(GenericInteractionCreateEvent event, Player player, List<String> techIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added techs:\n");
        for (String techID : techIDs) {
            if (player.getFactionTechs().contains(techID)) {
                sb.append("> ").append(techID).append(" (player had this faction tech)");
            } else {
                sb.append("> ").append(Mapper.getTech(techID).getRepresentation(true));
            }
            sb.append("\n");
            player.addFactionTech(techID);

            // ADD BASE UNIT IF ADDING UNIT UPGRADE TECH
            TechnologyModel techModel = Mapper.getTech(techID);
            if (techModel == null) continue;
            if (techModel.isUnitUpgrade()) {
                UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(techID);
                unitModel.getUpgradesFromUnitId().ifPresent(upgradesFromUnitId -> {
                    player.removeOwnedUnitByID(unitModel.getBaseType());
                    player.addOwnedUnitByID(upgradesFromUnitId);
                });
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

    public static void removeFactionTechs(GenericInteractionCreateEvent event, Player player, List<String> techIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed faction techs:\n");
        for (String techID : techIDs ){
            if (!player.getFactionTechs().contains(techID)) {
                sb.append("> ").append(techID).append(" (player did not have this tech)");
            } else {
                sb.append("> ").append(techID);
            }
            sb.append("\n");
            player.removeFactionTech(techID);

            // ADD BASE UNIT BACK IF REMOVING UNIT UPGRADE TECH
            TechnologyModel techModel = Mapper.getTech(techID);
            if (techModel == null) continue;
            if (techModel.isUnitUpgrade()) {
                UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(techID);
                player.removeOwnedUnitByID(unitModel.getAlias()); // remove the upgraded/base unit
                // remove the base/un-upgraded unit
                unitModel.getUpgradesFromUnitId().ifPresent(player::removeOwnedUnitByID);
                player.addOwnedUnitByID(unitModel.getBaseType()); // add the base unit back
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
