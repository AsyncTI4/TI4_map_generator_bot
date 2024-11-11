package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class FlipGrace extends PlayerGameStateSubcommand {

    public FlipGrace() {
        super(Constants.FLIP_GRACE, "Flip Grace (Edyn Faction Ability) to show it has been used", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        if (!player.hasAbility("grace")) {
            MessageHelper.sendMessageToEventChannel(event, "Player does not have Grace (Edyn Faction Ability)");
            return;
        }

        if (player.removeExhaustedAbility("grace")) {
            MessageHelper.sendMessageToEventChannel(event, "Grace (Edyn Faction Ability) was exhausted. Flipping it back to ready.");
            return;
        }

        player.addExhaustedAbility("grace");
        MessageHelper.sendMessageToEventChannel(event, "Grace (Edyn Faction Ability) exhausted");
    }

}
