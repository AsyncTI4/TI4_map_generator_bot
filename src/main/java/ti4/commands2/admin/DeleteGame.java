package ti4.commands2.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;
import ti4.service.game.EndGameService;

class DeleteGame extends Subcommand {

    DeleteGame() {
        super(Constants.DELETE_GAME, "Delete a game.");
        addOption(OptionType.STRING, Constants.GAME_NAME, "Game to delete", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME, OptionMapping::getAsString);
        if (gameName == null) return;

        if (!GameManager.isValid(gameName)) {
            MessageHelper.replyToMessage(event, "Invalid game name.");
            return;
        }
        Game game = GameManager.getManagedGame(gameName).getGame();
        if (!GameManager.delete(gameName)) {
            MessageHelper.replyToMessage(event, "Game failed to delete.");
            return;
        }

        EndGameService.secondHalfOfGameEnd(event, game, false, true, false);
        MessageHelper.replyToMessage(event, "Map: " + gameName + " deleted.");
    }
}
