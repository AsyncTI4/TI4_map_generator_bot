package ti4.map.manage;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.info.CardsInfoService;

@UtilityClass
class GameUndoService {

    public static void saveUndo(String gameName) {
        int latestIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(GameManager.getManagedGame(gameName));
        if (latestIndex < 0) return;
        File gameFile = Storage.getGameFile(gameName + Constants.TXT);
        if (gameFile.exists()) {
            createUndoCopy(gameFile, gameName, latestIndex + 1);
        }
    }

    private static void createUndoCopy(File originalGameFile, String gameName, int undoNumber) {
        try {
            File mapUndoStorage = Storage.getGameUndoStorage(gameName + "_" + undoNumber + Constants.TXT);
            Files.copy(originalGameFile.toPath(), mapUndoStorage.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            BotLogger.log("Error copying undo file for " + gameName, e);
        }
    }

    public static int cleanUpExcessUndoFilesAndReturnLatestIndex(ManagedGame game) {
        String gameName = game.getName();
        String gameNameFileNamePrefix = gameName + "_";
        var gameUndoPath = Storage.getGameUndoDirectory().toPath();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameUndoPath, game.getName() + "_*")) {
            List<Integer> undoNumbers = new ArrayList<>();
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                String undoNumberStr = StringUtils.substringBetween(fileName, gameNameFileNamePrefix, Constants.TXT);
                if (undoNumberStr != null) {
                    try {
                        undoNumbers.add(Integer.parseInt(undoNumberStr));
                    } catch (NumberFormatException e) {
                        BotLogger.log("Could not parse undo number '" + undoNumberStr + "' for game " + gameName, e);
                    }
                }
            }

            if (undoNumbers.isEmpty()) {
                return 0;
            }

            undoNumbers.sort(Integer::compareTo);
            int maxUndoNumber = undoNumbers.getLast();
            int maxUndoFilesPerGame = game.isHasEnded() ? 10 : 100;
            int oldestUndoNumberThatShouldExist = maxUndoNumber - maxUndoFilesPerGame;

            undoNumbers.stream()
                .filter(undoNumber -> undoNumber < oldestUndoNumberThatShouldExist)
                .map(undoNumber -> gameName + "_" + undoNumber + Constants.TXT)
                .forEach(fileName -> deleteFile(Storage.getGameUndoStoragePath(fileName)));

            return maxUndoNumber;
        } catch (Exception e) {
            BotLogger.log("Error trying clean up excess undo files for: " + gameName, e);
        }
        return -1;
    }

    public static void undo(Game game, GenericInteractionCreateEvent event) {
        File mapUndoDirectory = Storage.getGameUndoDirectory();
        if (!mapUndoDirectory.exists()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to undo: directory does not exist.");
            return;
        }
        File originalGameFile = Storage.getGameFile(game.getName() + Constants.TXT);
        if (!originalGameFile.exists()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to undo: cannot find game file.");
            return;
        }
        String gameName = game.getName();
        int latestUndoIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(GameManager.getManagedGame(gameName));
        if (latestUndoIndex <= 1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to undo: not enough available history.");
            return;
        }

        try {
            File previousUndo = Storage.getGameUndoStorage(gameName + "_" + (latestUndoIndex - 1) + Constants.TXT);
            Files.copy(previousUndo.toPath(), originalGameFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Game loadedGame = GameManager.reload(gameName);

            File latestUndo = Storage.getGameUndoStorage(gameName + "_" + latestUndoIndex  + Constants.TXT);
            if (loadedGame == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to undo: failed to load previous game state.");
                Files.copy(latestUndo.toPath(), originalGameFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            if (!latestUndo.delete()) {
                BotLogger.log("Failed to delete undo file: " + latestUndo.getAbsolutePath());
            }

            generateSavedButtons(event, loadedGame);

            for (Player p1 : loadedGame.getRealPlayers()) {
                Player p2 = game.getPlayerFromColorOrFaction(p1.getFaction());
                if (p2 != null && (p1.getAc() != p2.getAc() || p1.getSo() != p2.getSo())) {
                    CardsInfoService.sendCardsInfo(loadedGame, p1);
                }
            }

            StringBuilder sb = new StringBuilder("Rolled the game back, including this command:\n> `").append(latestUndoIndex).append("` ");
            if (loadedGame.getSavedChannel() instanceof ThreadChannel && loadedGame.getSavedChannel().getName().contains("Cards Info")) {
                sb.append("[CLASSIFIED]");
            } else {
                sb.append(loadedGame.getLatestCommand());
            }
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
            } else {
                ButtonHelper.findOrCreateThreadWithMessage(game, gameName + "-undo-log", sb.toString());
            }
        } catch (Exception e) {
            BotLogger.log("Error trying to undo: " + gameName, e);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unexpected error while processing undo. Check your game state!");
        }
    }

    private static void generateSavedButtons(GenericInteractionCreateEvent event, Game game) {
        try {
            if (!game.getSavedButtons().isEmpty() && game.getSavedChannel() != null && !game.getPhaseOfGame().contains("status")) {
                MessageHelper.sendMessageToChannelWithButtons(game.getSavedChannel(), game.getSavedMessage(), ButtonHelper.getSavedButtons(game));
            }
        } catch (Exception e) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to generate saved buttons...");
        }
    }

    private void undoWIP() {
        String targetUndoIndexStr = gameToUndoBackTo.replace(gameName + "_", "").replace(".txt", "");
        int targetUndoIndex = Integer.parseInt(targetUndoIndexStr);
        String targetUndoFileName = gameName + "_" + targetUndoIndex + ".txt";
        File targetUndoFile = new File(Storage.getGameUndoDirectory(), targetUndoFileName);
        if (!targetUndoFile.exists()) {
            MessageHelper.replyToMessage(event, "Undo failed - Couldn't find game to undo back to: " + targetUndoFileName);
            return;
        }

        Game gameToRestore = GameManager.reload(targetUndoFile);
        if (gameToRestore == null) {
            MessageHelper.replyToMessage(event,
                "Undo failed - Couldn't load game to undo back to: " + targetUndoFileName);
            return;
        }

        int latestUndoIndex = GameSaveService.cleanUpExcessUndoFilesAndReturnLatestIndex(GameManager.getManagedGame(gameName));
        StringBuilder sb = new StringBuilder(
            "Undoing Save #" + latestUndoIndex + " back to before Save #" + gameToUndoBackToNumber + ":\n");
        for (int i = latestUndoIndex; i > gameToUndoBackToNumber; i--) {
            String undoFile = gameName + "_" + i + ".txt";
            File undoFileToBeDeleted = new File(Storage.getGameUndoDirectory(), undoFile);
            if (undoFileToBeDeleted.exists()) {
                sb.append("> `").append(i).append("` ")
                    .append(undoFiles.get(undoFileToBeDeleted.getName())).append("\n");
                undoFileToBeDeleted.delete();
            }
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
        } else {
            ButtonHelper.findOrCreateThreadWithMessage(game, gameName + "-undo-log", sb.toString());
        }

        GameSaveService.undo(gameToRestore, event);
    }

    private static void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            BotLogger.log("Error trying to delete file: " + path, e);
        }
    }
}
