package ti4.discord.interactions.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.turn.PassService;

class Pass extends GameStateSubcommand {

    Pass() {
        super(Constants.PASS, "Pass", true, true);
        addOptions(CommonOptions.factionColorForStats());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        if (!game.getPlayedSCs().containsAll(player.getSCs())) {
            MessageHelper.sendMessageToEventChannel(event, "You have not played your strategy cards, you cannot pass.");
            return;
        }

        PassService.passPlayerForRound(event, game, player, false);
    }
}
