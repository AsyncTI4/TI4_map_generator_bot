package ti4.commands.admin;


import java.io.File;
import java.util.concurrent.ExecutionException;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class RestoreGame extends AdminSubcommandData {

    public RestoreGame() {
        super(Constants.RESTORE_GAME, "Restore a game by uploading a save file");
        addOptions(new OptionData(OptionType.ATTACHMENT, Constants.SAVE_FILE, "Save file to reload").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Attachment attachment = event.getOption(Constants.SAVE_FILE, null, OptionMapping::getAsAttachment);

        if (attachment == null) {
            MessageHelper.sendMessageToEventChannel(event, "No save file specified.");
            return;
        }
        if (!"txt".equals(attachment.getFileExtension())) {
            MessageHelper.sendMessageToEventChannel(event, "Save file must be a .txt file.");
            return;
        }
        if (!"text/plain; charset=utf-8".equals(attachment.getContentType())) {
            MessageHelper.sendMessageToEventChannel(event, "Save file must be text/plain; charset=utf-8");
            return;
        }
        File gameFile = Storage.getMapImageStorage(attachment.getFileName());
        try {
            gameFile = attachment.getProxy().downloadToFile(gameFile).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        Game game = GameSaveLoadManager.loadMap(gameFile);
        if (game == null) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to load game.");
            return;
        }
        if (!attachment.getFileName().equals(game.getName() + ".txt")) {
            MessageHelper.sendMessageToEventChannel(event, "Save file name must be the same as the game name.");
            return;
        }

        GameManager.getInstance().deleteGame(game.getName());
        GameManager.getInstance().addGame(game);
        MessageHelper.sendMessageToEventChannel(event, game.getName() + " restored.");
        GameSaveLoadManager.saveMap(game, event);
    }
}
