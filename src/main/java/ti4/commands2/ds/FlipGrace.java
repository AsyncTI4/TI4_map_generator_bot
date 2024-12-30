package ti4.commands2.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class FlipGrace extends GameStateSubcommand {

    public FlipGrace() {
        super(Constants.FLIP_GRACE, "Flip Grace (Edyn faction ability) to show it has been used", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        if (!player.hasAbility("grace")) {
            MessageHelper.sendMessageToEventChannel(event, "Player does not have **Grace** (Edyn faction ability).");
            return;
        }

        if (player.removeExhaustedAbility("grace")) {
            MessageHelper.sendMessageToEventChannel(event, "**Grace** (Edyn faction ability) was exhausted. Flipping it back to ready.");
            return;
        }

        player.addExhaustedAbility("grace");
        MessageHelper.sendMessageToEventChannel(event, "**Grace** (Edyn faction ability) has been exhausted.");
    }

}
