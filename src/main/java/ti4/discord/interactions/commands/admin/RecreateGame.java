package ti4.discord.interactions.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.RecreateGameService;

class RecreateGame extends GameStateSubcommand {

    RecreateGame() {
        super(Constants.RECREATE_GAME, "Recreate a game's channels and roles.", true, false);
        addOption(OptionType.STRING, Constants.GAME_NAME, "Game to recreate", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var game = getGame();
        MessageHelper.replyToMessage(event, RecreateGameService.recreateGame(game));
    }
}
