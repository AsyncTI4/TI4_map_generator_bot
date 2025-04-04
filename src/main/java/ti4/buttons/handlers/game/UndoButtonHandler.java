package ti4.buttons.handlers.game;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class UndoButtonHandler {

    @ButtonHandler(value = "ultimateUndo_", save = false)
    public static void ultimateUndo_(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.getSavedButtons().isEmpty()) {
            String buttonString = game.getSavedButtons().getFirst();
            String colorOrFaction = buttonString.split(";")[0];
            Player p = game.getPlayerFromColorOrFaction(colorOrFaction);
            if (p != null && player != p && !colorOrFaction.equals("null")) {
                // if the last button was pressed by a non-faction player, allow anyone to undo
                // it
                String msg = "You were not the player who pressed the latest button. Use `/game undo` if you truly wish to undo "
                    + game.getLatestCommand() + ".";
                MessageHelper.sendMessageToChannel(event.getChannel(), msg);
                return;
            }
        }

        String highestNumBefore = buttonID.split("_")[1];
        Path gameUndoDirectory = Storage.getGameUndoDirectory(game.getName());
        String gameName = game.getName();
        String gameNameForUndoStart = gameName + "_";

        List<Integer> numbers = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameUndoDirectory, gameNameForUndoStart + "*")) {
            for (Path filePath : stream) {
                String fileName = filePath.getFileName().toString()
                    .replace(gameNameForUndoStart, "")
                    .replace(Constants.TXT, "");
                numbers.add(Integer.parseInt(fileName));
            }
        } catch (IOException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "Error while reading game undo directory: " + gameUndoDirectory, e);
        }

        int maxNumber = numbers.isEmpty() ? 0
            : numbers.stream()
                .mapToInt(value -> value)
                .max()
                .orElseThrow(NoSuchElementException::new);
        
        if (highestNumBefore.equalsIgnoreCase(String.valueOf(maxNumber - 1)) || highestNumBefore.equalsIgnoreCase(String.valueOf(maxNumber + 1))) {
            ButtonHelper.deleteMessage(event);
        }

        Game undo = GameManager.undo(game);
        if (undo == null) {
            event.getHook().sendMessage("Failed to undo.").setEphemeral(true).queue();
            return;
        }

        StringBuilder msg = new StringBuilder("You undid something, the details of which can be found in the `#undo-log` thread");
        List<ThreadChannel> threadChannels = game.getMainGameChannel().getThreadChannels();
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(game.getName() + "-undo-log")) {
                msg.append(": ").append(threadChannel_.getJumpUrl());
                break;
            }
        }
        event.getHook().sendMessage(msg + ".").setEphemeral(true).queue();
    }
}
