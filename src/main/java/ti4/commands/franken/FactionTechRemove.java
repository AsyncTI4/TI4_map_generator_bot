package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class FactionTechRemove extends FactionTechAddRemove {

    public FactionTechRemove() {
        super(Constants.FACTION_TECH_REMOVE, "Remove a faction tech from your faction");
    }
    
    @Override
    public void doAction(Player player, List<String> techIDs, SlashCommandInteractionEvent event) {
        removeFactionTechs(event, player, techIDs);
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
