package ti4.commands.game;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Undo extends GameSubcommandData {
    public Undo() {
        super(Constants.UNDO, "Undo the last action");
        addOptions(new OptionData(OptionType.STRING, Constants.UNDO_TO_BEFORE_COMMAND, "Command to undo back to").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GameManager gameManager = GameManager.getInstance();
        Game game = gameManager.getUserActiveGame(event.getUser().getId());
        if (game == null) {
            MessageHelper.replyToMessage(event, "Must set active Game");
            return;
        }
        if (!event.getChannel().getName().startsWith(game.getName() + "-")) {
            MessageHelper.replyToMessage(event, "Undo must be executed in game channel only!");
            return;
        }

        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Undo failed - Must confirm with YES");
            return;
        }

        String gameToUndoBackTo = event.getOption(Constants.UNDO_TO_BEFORE_COMMAND, null, OptionMapping::getAsString);
        if (gameToUndoBackTo == null || gameToUndoBackTo.isEmpty()) {
            MessageHelper.replyToMessage(event, "Must specify command to undo back to");
            return;
        }
        if (gameToUndoBackTo.toLowerCase().contains("fog of war")) {
            MessageHelper.replyToMessage(event, "Game is Fog of War - limited to a single undo at a time.");
            GameSaveLoadManager.undo(game, event);
            return;
        }
        if (!gameToUndoBackTo.contains(game.getName())) {
            MessageHelper.replyToMessage(event, "Undo failed - Parameter doesn't look right: " + gameToUndoBackTo);
            return;
        }
        String intToUndoBackTo = gameToUndoBackTo.replace(game.getName() + "_", "").replace(".txt", "");

        int gameToUndoBackToNumber = Integer.parseInt(intToUndoBackTo);

        Map<String, Game> undoFiles = getAllUndoSavedGames(game);
        int maxSaveNumber = undoFiles.keySet().stream().map(s -> s.replace(game.getName() + "_", "").replace(".txt", ""))
            .mapToInt(Integer::parseInt).max().orElseThrow(NoSuchElementException::new);

        String undoFileToRestorePath = game.getName() + "_" + gameToUndoBackToNumber + ".txt";
        File undoFileToRestore = new File(Storage.getGameUndoDirectory(), undoFileToRestorePath);
        if (!undoFileToRestore.exists()) {
            MessageHelper.replyToMessage(event, "Undo failed - Couldn't find game to undo back to: " + undoFileToRestorePath);
            return;
        }
        Game gameToRestore = GameSaveLoadManager.loadGame(undoFileToRestore);
        if (gameToRestore == null) {
            MessageHelper.replyToMessage(event,
                "Undo failed - Couldn't load game to undo back to: " + undoFileToRestorePath);
            return;
        }

        StringBuilder sb = new StringBuilder(
            "Undoing Save #" + maxSaveNumber + " back to before Save #" + gameToUndoBackToNumber + ":\n");
        for (int i = maxSaveNumber; i > gameToUndoBackToNumber; i--) {
            String undoFile = game.getName() + "_" + i + ".txt";
            File undoFileToBeDeleted = new File(Storage.getGameUndoDirectory(), undoFile);
            if (undoFileToBeDeleted.exists()) {
                sb.append("> `").append(i).append("` ")
                    .append(undoFiles.get(undoFileToBeDeleted.getName()).getLatestCommand()).append("\n");
                undoFileToBeDeleted.delete();
            }
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
        } else {
            ButtonHelper.findOrCreateThreadWithMessage(game, game.getName() + "-undo-log", sb.toString());
        }
        // MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());

        GameManager.getInstance().deleteGame(game.getName());
        GameManager.getInstance().addGame(gameToRestore);
        GameSaveLoadManager.undo(gameToRestore, event);
    }

    @ButtonHandler("ultimateUndo_")
    public static void ultimateUndo_(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.getSavedButtons().isEmpty()) {
            String buttonString = game.getSavedButtons().getFirst();
            String colorOrFaction = buttonString.split(";")[0];
            Player p = game.getPlayerFromColorOrFaction(colorOrFaction);
            if (p != null && player != p && !colorOrFaction.equals("null")) {
                // if the last button was pressed by a non-faction player, allow anyone to undo
                // it
                String msg = "You were not the player who pressed the latest button. Use /game undo if you truly want to undo "
                    + game.getLatestCommand();
                MessageHelper.sendMessageToChannel(event.getChannel(), msg);
                return;
            }
        }
        String highestNumBefore = buttonID.split("_")[1];
        File mapUndoDirectory = Storage.getGameUndoDirectory();
        if (!mapUndoDirectory.exists()) {
            return;
        }
        String mapName = game.getName();
        String mapNameForUndoStart = mapName + "_";
        String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
        if (mapUndoFiles != null && mapUndoFiles.length > 0) {
            List<Integer> numbers = Arrays.stream(mapUndoFiles)
                .map(fileName -> fileName.replace(mapNameForUndoStart, ""))
                .map(fileName -> fileName.replace(Constants.TXT, ""))
                .map(Integer::parseInt).toList();
            int maxNumber = numbers.isEmpty() ? 0 : numbers.stream().mapToInt(value -> value).max().orElseThrow(NoSuchElementException::new);
            if (highestNumBefore.equalsIgnoreCase((maxNumber) + "")) {
                ButtonHelper.deleteMessage(event);
            }
        }

        GameSaveLoadManager.undo(game, event);

        StringBuilder msg = new StringBuilder("You undid something, the details of which can be found in the undo-log thread");
        List<ThreadChannel> threadChannels = game.getMainGameChannel().getThreadChannels();
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(game.getName() + "-undo-log")) {
                msg.append(": ").append(threadChannel_.getJumpUrl());
            }
        }
        event.getHook().sendMessage(msg.toString()).setEphemeral(true).queue();
    }

    public static Map<String, Game> getAllUndoSavedGames(Game game) {
        File mapUndoDirectory = Storage.getGameUndoDirectory();
        String mapName = game.getName();
        String mapNameForUndoStart = mapName + "_";
        String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
        return Arrays.stream(mapUndoFiles).map(Storage::getGameUndoStorage)
            .collect(Collectors.toMap(File::getName, GameSaveLoadManager::loadGame));
    }
}
