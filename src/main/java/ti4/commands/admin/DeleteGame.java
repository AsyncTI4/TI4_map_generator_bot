package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.Subcommand;
import ti4.commands.game.GameEnd;
import ti4.helpers.Constants;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

class DeleteGame extends Subcommand {

    DeleteGame() {
        super(Constants.DELETE_GAME, "Delete a game.");
        addOption(OptionType.STRING, Constants.GAME_NAME, "Game to delete", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME, OptionMapping::getAsString);
        if (gameName == null) return;

        var gameToDelete = GameManager.getGame(gameName);
        if (gameToDelete == null) {
            MessageHelper.replyToMessage(event, "Map: " + gameName + " was not found.");
            return;
        }

        if (GameSaveLoadManager.deleteGame(gameName)) {
            GameEnd.secondHalfOfGameEnd(event, gameToDelete, false, true, false);
            MessageHelper.replyToMessage(event, "Map: " + gameName + " deleted.");
        } else {
            MessageHelper.replyToMessage(event, "Map could not be deleted");
        }
    }
}
