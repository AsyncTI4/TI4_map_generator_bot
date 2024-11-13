package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class AbilityRemove extends AbilityAddRemove {

    public AbilityRemove() {
        super(Constants.ABILITY_REMOVE, "Remove an ability from your faction");
    }

    @Override
    public void doAction(Player player, List<String> abilityIDs, SlashCommandInteractionEvent event) {
        removeAbilities(event, player, abilityIDs);
    }

    public static void removeAbilities(GenericInteractionCreateEvent event, Player player, List<String> abilityIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed abilities:\n");
        for (String abilityID : abilityIDs) {
            if (!player.hasAbility(abilityID)) {
                sb.append("> ").append(abilityID).append(" (player did not have this ability)");
            } else {
                sb.append("> ").append(abilityID);
            }
            sb.append("\n");
            player.removeAbility(abilityID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
