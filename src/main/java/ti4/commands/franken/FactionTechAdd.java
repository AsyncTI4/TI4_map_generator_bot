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

class FactionTechAdd extends FactionTechAddRemove {

    public FactionTechAdd() {
        super(Constants.FACTION_TECH_ADD, "Add a faction tech to your faction");
    }

    @Override
    public void doAction(Player player, List<String> techIDs, SlashCommandInteractionEvent event) {
        addFactionTechs(event, player, techIDs);
    }

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
}
