package ti4.commands.game;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
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

        Integer gameToUndoBackToNumber = Integer.parseInt(intToUndoBackTo);

        Map<String, Game> undoFiles = getAllUndoSavedGames(game);
        Integer maxSaveNumber = undoFiles.keySet().stream().map(s -> s.replace(game.getName() + "_", "").replace(".txt", ""))
            .mapToInt(Integer::parseInt).max().orElseThrow(NoSuchElementException::new);

        String undoFileToRestorePath = game.getName() + "_" + gameToUndoBackToNumber + ".txt";
        File undoFileToRestore = new File(Storage.getMapUndoDirectory(), undoFileToRestorePath);
        if (!undoFileToRestore.exists()) {
            MessageHelper.replyToMessage(event, "Undo failed - Couldn't find game to undo back to: " + undoFileToRestorePath);
            return;
        }
        Game gameToRestore = GameSaveLoadManager.loadMap(undoFileToRestore);
        if (gameToRestore == null) {
            MessageHelper.replyToMessage(event,
                "Undo failed - Couldn't load game to undo back to: " + undoFileToRestorePath);
            return;
        }

        StringBuilder sb = new StringBuilder(
            "Undoing Save #" + maxSaveNumber + " back to Save #" + gameToUndoBackToNumber + ":\n");
        for (int i = maxSaveNumber; i >= gameToUndoBackToNumber; i--) {
            String undoFile = game.getName() + "_" + i + ".txt";
            File undoFileToBeDeleted = new File(Storage.getMapUndoDirectory(), undoFile);
            if (undoFileToBeDeleted.exists()) {
                sb.append("> `").append(i).append("` ")
                    .append(undoFiles.get(undoFileToBeDeleted.getName()).getLatestCommand()).append("\n");
                undoFileToBeDeleted.delete();
            }
        }
        if (game.isFoWMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
        } else {
            ButtonHelper.findOrCreateThreadWithMessage(game, game.getName() + "-undo-log", sb.toString());
        }
        // MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());

        GameManager.getInstance().deleteGame(game.getName());
        GameManager.getInstance().addGame(gameToRestore);
        GameSaveLoadManager.undo(gameToRestore, event);
    }

    public static Map<String, Game> getAllUndoSavedGames(Game game) {
        File mapUndoDirectory = Storage.getMapUndoDirectory();
        String mapName = game.getName();
        String mapNameForUndoStart = mapName + "_";
        String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
        return Arrays.stream(mapUndoFiles).map(Storage::getMapUndoStorage)
            .sorted(Comparator.comparing(File::getName).reversed())
            .collect(Collectors.toMap(File::getName, GameSaveLoadManager::loadMap));
    }
}
