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
        if (player.getGame().isTwilightsFallMode()) {
            addTF_Techs(event, player, techIDs);
            return;
        }

        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added technologies:\n");
        for (String techID : techIDs) {
            if (player.getFactionTechs().contains(techID)) {
                sb.append("> ").append(techID).append(" (player had this faction technology)");
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
        MessageHelper.sendEphemeralMessageToEventChannel(event, sb.toString());
    }

    public static void removeFactionTechs(GenericInteractionCreateEvent event, Player player, List<String> techIDs) {
        if (player.getGame().isTwilightsFallMode()) {
            removeTF_Techs(event, player, techIDs);
            return;
        }

        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed faction technologies:\n");
        for (String techID : techIDs) {
            if (!player.getFactionTechs().contains(techID)) {
                sb.append("> ").append(techID).append(" (player did not have this technology)");
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
        MessageHelper.sendEphemeralMessageToEventChannel(event, sb.toString());
    }

    private void addTF_Techs(GenericInteractionCreateEvent event, Player player, List<String> techIDs) {
        for (String tech : techIDs) {
            if (player.getGame().isVeiledHeartMode()) {
                String msg = "Added a veiled card. Refresh your `#cards-info` thread to find a button to reveal it";
                MessageHelper.sendEphemeralMessageToEventChannel(event, msg);

                String key = "veiledCards" + player.getFaction();
                String val = player.getGame().getStoredValue("veiledCards" + player.getFaction()) + tech + "_";
                player.getGame().setStoredValue(key, val);
            } else {
                player.addTech(tech);
            }
        }
    }

    private void removeTF_Techs(GenericInteractionCreateEvent event, Player player, List<String> techIDs) {
        for (String tech : techIDs) {
            if (player.getGame().isVeiledHeartMode()) {
                String msg = "Removed a veiled card. Refresh your `#cards-info` thread to find a button to reveal it";
                MessageHelper.sendEphemeralMessageToEventChannel(event, msg);

                String key = "veiledCards" + player.getFaction();
                String val = player.getGame().getStoredValue("veiledCards" + player.getFaction());
                val = val.replace(tech + "_", "");
                player.getGame().setStoredValue(key, val);
            } else {
                player.addTech(tech);
            }
        }
    }
}
