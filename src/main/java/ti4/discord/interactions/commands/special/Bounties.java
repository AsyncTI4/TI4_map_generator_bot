package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.zephyrion.ZephyrionBountyHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class Bounties extends GameStateSubcommand {

    Bounties() {
        super(Constants.BOUNTIES, "Show current bounties and manage bounty tokens", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        if (game.getRealPlayers().stream().noneMatch(p -> p.hasAbility("marked_prey"))) {
            MessageHelper.sendMessageToEventChannel(
                    event, "No player in this game has the Marked Prey ability, so there are no bounties.");
            return;
        }
        if (!player.hasAbility("marked_prey")) {
            String currentBounties = String.join(", ", ZephyrionBountyHandler.getBountiesForPlayer(game));
            MessageHelper.sendMessageToEventChannel(
                    event, "Current bounties: " + (currentBounties.isEmpty() ? "none." : currentBounties + "."));
            return;
        }
        ZephyrionBountyHandler.offerBountyButtons(game, player);
    }
}
