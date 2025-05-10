package ti4.map.manage;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.service.game.GameUndoNameService;
import ti4.service.info.CardsInfoService;

@UtilityClass
class GameUndoService {

    public static void createUndoCopy(String gameName) {
        GameFileLockManager.wrapWithReadLock(gameName, () -> {
            int latestIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(gameName);
            if (latestIndex < 0) return;
            File gameFile = Storage.getGameFile(gameName + Constants.TXT);
            if (!gameFile.exists()) return;
            try {
                Path mapUndoStorage = Storage.getGameUndo(gameName, getUndoFileName(gameName, latestIndex + 1));
                Files.copy(gameFile.toPath(), mapUndoStorage, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                BotLogger.error("Error copying undo file for " + gameName, e);
            }
        });
    }

    private static int cleanUpExcessUndoFilesAndReturnLatestIndex(String gameName) {
        try {
            List<Integer> undoNumbers = GameUndoNameService.getSortedUndoNumbers(gameName);
            if (undoNumbers.isEmpty()) return 0;
            
            int maxUndoNumber = undoNumbers.getLast();
            int maxUndoFilesPerGame = GameManager.getManagedGame(gameName).isHasEnded() ? 10 : 100;
            int oldestUndoNumberThatShouldExist = maxUndoNumber - maxUndoFilesPerGame;

            undoNumbers.stream()
                .filter(undoIndex -> undoIndex < oldestUndoNumberThatShouldExist)
                .map(undoIndex -> getUndoFileName(gameName, undoIndex))
                .forEach(fileName -> deleteFile(Storage.getGameUndo(gameName, fileName)));

            return maxUndoNumber;
        } catch (Exception e) {
            BotLogger.error("Error trying clean up excess undo files for: " + gameName, e);
        }
        return -1;
    }

    @Nullable
    public static Game undo(Game game) {
        int latestUndoIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(game.getName());
        return lockAndUndo(game, latestUndoIndex - 1, latestUndoIndex);
    }

    @Nullable
    public static Game undo(Game game, int undoIndex) {
        if (undoIndex <= 0) return null;
        int latestUndoIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(game.getName());
        return lockAndUndo(game, undoIndex, latestUndoIndex);
    }

    private static Game lockAndUndo(Game gameToUndo, int undoIndex, int latestUndoIndex) {
        return GameFileLockManager.wrapWithWriteLock(gameToUndo.getName(), () -> undo(gameToUndo, undoIndex, latestUndoIndex));
    }

    private static Game undo(Game gameToUndo, int undoIndex, int latestUndoIndex) {
        if (latestUndoIndex <= 1) return null;
        String gameName = gameToUndo.getName();
        try {
            File currentGameFile = Storage.getGameFile(gameName + Constants.TXT);
            if (!currentGameFile.exists()) {
                BotLogger.error(new BotLogger.LogMessageOrigin(gameToUndo), "Game file for " + gameName + " doesn't exist!");
                return null;
            }

            replaceGameFileWithUndo(gameName, undoIndex, currentGameFile.toPath());
            Game loadedGame = GameLoadService.load(gameName);
            if (loadedGame == null) { // rollback if we failed to load the undo
                replaceGameFileWithUndo(gameName, latestUndoIndex, currentGameFile.toPath());
                return null;
            }

            generateSavedButtons(gameToUndo);
            sendAnyChangedCardsInfo(gameToUndo, loadedGame);
            GameMessageManager.removeAfter(gameName, loadedGame.getLastModifiedDate());

            sendUndoConfirmationMessage(gameToUndo, undoIndex, latestUndoIndex);
            return loadedGame;
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(gameToUndo), "Error trying to undo: " + gameName, e);
            return null;
        }
    }

    private static void sendAnyChangedCardsInfo(Game game, Game loadedGame) {
        for (Player p1 : loadedGame.getRealPlayers()) {
            Player p2 = game.getPlayerFromColorOrFaction(p1.getFaction());
            if (p2 != null && (p1.getAc() != p2.getAc() || p1.getSo() != p2.getSo())) {
                CardsInfoService.sendCardsInfo(loadedGame, p1);
            }
        }
    }

    private static void replaceGameFileWithUndo(String gameName, int undoIndex, Path gameFilePath) throws IOException {
        Path undoFilePath = Storage.getGameUndo(gameName, getUndoFileName(gameName, undoIndex));
        Files.copy(undoFilePath, gameFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String getUndoFileName(String gameName, int undoIndex) {
        return gameName + "_" + undoIndex + Constants.TXT;
    }

    private static void sendUndoConfirmationMessage(Game gameToUndo, int undoIndex, int latestUndoIndex) {
        if (gameToUndo.isFowMode()) {
            return;
        }
        Map<String, String> undoNamesToCommandText = GameUndoNameService.getUndoNamesToCommandText(gameToUndo, latestUndoIndex - undoIndex);
        List<String> undoCommands = new ArrayList<>();
        for (int i = latestUndoIndex; i > undoIndex; i--) {
            String fileName = getUndoFileName(gameToUndo.getName(), i);
            undoCommands.add(undoNamesToCommandText.get(fileName));
            Path currentUndo = Storage.getGameUndo(gameToUndo.getName(), fileName);
            if (!currentUndo.toFile().delete()) {
                BotLogger.error(new BotLogger.LogMessageOrigin(gameToUndo), "Failed to delete undo file: " + currentUndo);
            }
        }

        sendUndoConfirmationMessage(gameToUndo, undoIndex, latestUndoIndex, undoCommands);
    }

    private static void sendUndoConfirmationMessage(Game game, int undoIndex, int latestUndoIndex, List<String> undoCommands) {
        StringBuilder sb = new StringBuilder("Rolled back to save `").append(undoIndex).append("` from `").append(latestUndoIndex).append("`:\n");

        String gameName = game.getName();
        for (int i = 0; i < undoCommands.size(); i++) {
            sb.append("> `").append(latestUndoIndex - i).append("` ").append(undoCommands.get(i)).append("\n");
        }
        ButtonHelper.findOrCreateThreadWithMessage(game, gameName + "-undo-log", sb.toString());
    }

    private static void generateSavedButtons(Game game) {
        try {
            if (!game.getSavedButtons().isEmpty() && game.getSavedChannel() != null && !game.getPhaseOfGame().contains("status")) {
                MessageHelper.sendMessageToChannelWithButtons(game.getSavedChannel(), game.getSavedMessage(), ButtonHelper.getSavedButtons(game));
            }
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Error trying to generated saved buttons for " + game.getName(), e);
        }
    }

    private static void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            BotLogger.error("Error trying to delete file: " + path, e);
        }
    }

    public static Game loadUndoForMissingGame(String gameName) {
        int latestUndoIndex = GameUndoNameService.getSortedUndoNumbers(gameName).getLast();
        File currentGameFile = Storage.getGameFile(gameName + Constants.TXT);
        try {
            replaceGameFileWithUndo(gameName, latestUndoIndex, currentGameFile.toPath());
            return GameLoadService.load(gameName);
        } catch (IOException e) {
            BotLogger.error("Error trying to undo for missing game: " + gameName, e);
        }
        return null;
    }
}
