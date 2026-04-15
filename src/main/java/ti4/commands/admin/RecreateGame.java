package ti4.commands.admin;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.Subcommand;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.RecreateGameService;

class RecreateGame extends Subcommand {

    RecreateGame() {
        super(Constants.RECREATE_GAME, "Recreate a game's channels and roles.");
        addOption(OptionType.STRING, Constants.GAME_NAME, "Game to recreate", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME, OptionMapping::getAsString);
        if (gameName == null || !GameManager.isValid(gameName)) {
            MessageHelper.replyToMessage(event, "Invalid game name.");
            return;
        }

        Game game = GameManager.getManagedGame(gameName).getGame();
        Guild guild = game.getGuild() != null ? game.getGuild() : event.getGuild();
        if (guild == null) {
            MessageHelper.replyToMessage(event, "Could not determine which guild should host this game.");
            return;
        }

        RecreateGameService.RecreateGameResult result = RecreateGameService.recreateGame(game, guild);
        MessageHelper.replyToMessage(event, result.getSummary());
    }
}
