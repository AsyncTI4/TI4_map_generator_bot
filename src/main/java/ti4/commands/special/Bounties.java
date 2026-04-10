package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.buttons.handlers.faction.zephyrion.ZephyrionBountyButtonHandler;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class Bounties extends GameStateSubcommand {

    Bounties() {
        super(Constants.BOUNTIES, "Show current bounties and manage bounty tokens", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        if (!player.hasAbility("marked_prey")) {
            MessageHelper.sendMessageToEventChannel(
                    event, "You do not have the Marked Prey ability and cannot manage bounties.");
            return;
        }
        ZephyrionBountyButtonHandler.offerBountyButtons(game, player);
    }
}
