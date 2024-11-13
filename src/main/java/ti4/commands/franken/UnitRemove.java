package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class UnitRemove extends UnitAddRemove {

    public UnitRemove() {
        super(Constants.UNIT_REMOVE, "Remove an unit from your faction");
    }

    @Override
    public void doAction(Player player, List<String> unitIDs, SlashCommandInteractionEvent event) {
        removeUnits(event, player, unitIDs);
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
