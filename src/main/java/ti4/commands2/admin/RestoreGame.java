package ti4.commands2.admin;

import java.io.File;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;

class RestoreGame extends Subcommand {

    public RestoreGame() {
        super(Constants.RESTORE_GAME, "Restore a game by uploading a save file");
        addOptions(
            new OptionData(OptionType.ATTACHMENT, Constants.SAVE_FILE, "Save file to reload").setRequired(true),
            new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name to load to").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Attachment attachment = event.getOption(Constants.SAVE_FILE).getAsAttachment();
        if (!"txt".equals(attachment.getFileExtension())) {
            MessageHelper.sendMessageToEventChannel(event, "Save file must be a .txt file.");
            return;
        }
        if (!"text/plain; charset=utf-8".equals(attachment.getContentType())) {
            MessageHelper.sendMessageToEventChannel(event, "Save file must be text/plain; charset=utf-8");
            return;
        }
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!attachment.getFileName().equals(gameName + ".txt")) {
            MessageHelper.sendMessageToEventChannel(event, "Save file name must be the same as the game name.");
            return;
        }
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid game name.");
            return;
        }

        try {
            File gameFile = Storage.getGameFile(attachment.getFileName());
             attachment.getProxy().downloadToFile(gameFile).get();
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to download game attachment.");
            return;
        }

        Game game = GameManager.reload(gameName);
        if (game == null) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to restore game.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(event, game.getName() + " restored.");
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getName() + " was restored.");
    }
}
