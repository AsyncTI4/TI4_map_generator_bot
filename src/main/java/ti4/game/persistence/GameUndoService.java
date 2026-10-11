package ti4.game.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.service.game.GameUndoNameService;
import ti4.service.info.CardsInfoService;
import ti4.spring.websocket.GameWebStatePipeline;

@UtilityClass
class GameUndoService {

    static int createUndoCopy(String gameName) {
        return GameFileLockManager.wrapWithReadLock(gameName, () -> {
            int latestIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(gameName);
            if (latestIndex < 0) return -1;
            File gameFile = Storage.getGameFile(gameName + Constants.TXT);
            if (!gameFile.exists()) return -1;
            try {
                int createdUndoIndex = latestIndex + 1;
                Path mapUndoStorage = Storage.getGameUndo(gameName, getUndoFileName(gameName, createdUndoIndex));
                Files.copy(gameFile.toPath(), mapUndoStorage, StandardCopyOption.REPLACE_EXISTING);
                return createdUndoIndex;
            } catch (Exception e) {
                BotLogger.error("Error copying undo file for " + gameName, e);
                return -1;
            }
        });
    }

    private static int cleanUpExcessUndoFilesAndReturnLatestIndex(String gameName) {
        try {
            List<Integer> undoNumbers = GameUndoNameService.getSortedUndoNumbers(gameName);
            if (undoNumbers.isEmpty()) return 0;

            int maxUndoNumber = undoNumbers.getLast();
            ManagedGame managedGame = GameManager.getManagedGame(gameName);
            int maxUndoFilesPerGame = managedGame != null && managedGame.isHasEnded() ? 10 : 100;
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
    static Game undo(Game game) {
        return lockAndUndo(game, latestUndoIndex -> latestUndoIndex - 1, null);
    }

    @Nullable
    static Game undo(Game game, int undoIndex, @Nullable Long selectedSaveLastModifiedDate) {
        return lockAndUndo(game, latestUndoIndex -> undoIndex, selectedSaveLastModifiedDate);
    }

    private static Game lockAndUndo(
            Game gameToUndo, IntUnaryOperator undoIndexFromLatest, @Nullable Long selectedSaveLastModifiedDate) {
        String gameName = gameToUndo.getName();
        return GameFileLockManager.wrapWithWriteLock(gameName, () -> {
            int latestUndoIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(gameName);
            int undoIndex = undoIndexFromLatest.applyAsInt(latestUndoIndex);
            if (!isUndoTargetAvailable(gameName, undoIndex, latestUndoIndex, selectedSaveLastModifiedDate)) {
                BotLogger.info(
                        new LogOrigin(gameToUndo),
                        "Undo save `" + undoIndex + "` (latest `" + latestUndoIndex + "`) is no longer available for "
                                + gameName + ".");
                return null;
            }
            return undo(gameToUndo, undoIndex, latestUndoIndex);
        });
    }

    private static boolean isUndoTargetAvailable(
            String gameName, int undoIndex, int latestUndoIndex, @Nullable Long selectedSaveLastModifiedDate) {
        if (undoIndex < 1 || undoIndex >= latestUndoIndex) return false;
        if (!Files.exists(getUndoFilePath(gameName, undoIndex))) return false;
        Path selectedSave = getUndoFilePath(gameName, undoIndex + 1);
        if (!Files.exists(selectedSave)) return false;
        return selectedSaveLastModifiedDate == null
                || selectedSaveLastModifiedDate.equals(GameUndoNameService.readLastModifiedDate(selectedSave));
    }

    private static Game undo(Game gameToUndo, int undoIndex, int latestUndoIndex) {
        String gameName = gameToUndo.getName();
        Path currentGameFile = Storage.getGameFile(gameName + Constants.TXT).toPath();
        if (!Files.exists(currentGameFile)) {
            BotLogger.error(new LogOrigin(gameToUndo), "Game file for " + gameName + " doesn't exist!");
            return null;
        }

        RestoredUndo restored = restoreUndoSave(gameToUndo, undoIndex, latestUndoIndex, currentGameFile);
        if (restored == null) return null;
        Game loadedGame = restored.loadedGame();

        runAfterUndo(gameToUndo, "queue the web state", () -> GameWebStatePipeline.queue(loadedGame));
        generateSavedButtons(Objects.requireNonNullElse(restored.savedButtonsGame(), gameToUndo));
        runAfterUndo(gameToUndo, "send changed card info", () -> sendAnyChangedCardsInfo(gameToUndo, loadedGame));
        runAfterUndo(
                gameToUndo,
                "remove undone messages",
                () -> GameMessageManager.removeAfter(gameName, loadedGame.getLastModifiedDate()));
        removeUndoneSaves(gameToUndo, undoIndex, latestUndoIndex);
        return loadedGame;
    }

    private record RestoredUndo(Game loadedGame, @Nullable Game savedButtonsGame) {}

    @Nullable
    private static RestoredUndo restoreUndoSave(
            Game gameToUndo, int undoIndex, int latestUndoIndex, Path currentGameFile) {
        String gameName = gameToUndo.getName();
        byte[] originalGameFile;
        try {
            originalGameFile = Files.readAllBytes(currentGameFile);
        } catch (IOException e) {
            BotLogger.error(new LogOrigin(gameToUndo), "Could not read game file before undo: " + gameName, e);
            return null;
        }

        try {
            Game savedButtonsGame = null;
            if (undoIndex != latestUndoIndex - 1) {
                replaceGameFileWithUndo(gameName, undoIndex + 1, currentGameFile);
                savedButtonsGame = GameLoadService.load(gameName);
            }
            replaceGameFileWithUndo(gameName, undoIndex, currentGameFile);
            Game loadedGame = GameLoadService.load(gameName);
            if (loadedGame != null) return new RestoredUndo(loadedGame, savedButtonsGame);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(gameToUndo), "Error trying to undo: " + gameName, e);
        }

        restoreGameFile(gameToUndo, currentGameFile, originalGameFile);
        return null;
    }

    private static void restoreGameFile(Game gameToUndo, Path currentGameFile, byte[] originalGameFile) {
        try {
            Files.write(currentGameFile, originalGameFile);
        } catch (IOException e) {
            BotLogger.critical(
                    new LogOrigin(gameToUndo),
                    "Failed to restore " + gameToUndo.getName()
                            + " after a failed undo; the game file may hold an undo save.",
                    e);
        }
    }

    private static void runAfterUndo(Game game, String description, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            BotLogger.error(
                    new LogOrigin(game), "Undo of " + game.getName() + " succeeded, but failed to " + description, e);
        }
    }

    private static void sendAnyChangedCardsInfo(Game game, Game loadedGame) {
        for (Player p1 : loadedGame.getRealPlayers()) {
            Player p2 = game.getPlayerFromColorOrFaction(p1.getFaction());
            if (p2 != null && (p1.getAcCount() != p2.getAcCount() || p1.getSo() != p2.getSo())) {
                CardsInfoService.sendCardsInfo(loadedGame, p1);
            }
        }
    }

    private static void replaceGameFileWithUndo(String gameName, int undoIndex, Path gameFilePath) throws IOException {
        Files.copy(getUndoFilePath(gameName, undoIndex), gameFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path getUndoFilePath(String gameName, int undoIndex) {
        return Storage.getGameUndo(gameName, getUndoFileName(gameName, undoIndex));
    }

    private static String getUndoFileName(String gameName, int undoIndex) {
        return gameName + "_" + undoIndex + Constants.TXT;
    }

    private static void removeUndoneSaves(Game gameToUndo, int undoIndex, int latestUndoIndex) {
        if (gameToUndo.isFowMode()) {
            return;
        }
        Map<String, String> undoNamesToCommandText =
                GameUndoNameService.getUndoNamesToCommandText(gameToUndo, latestUndoIndex - undoIndex);
        List<String> undoCommands = new ArrayList<>();
        for (int i = latestUndoIndex; i > undoIndex; i--) {
            String fileName = getUndoFileName(gameToUndo.getName(), i);
            undoCommands.add(undoNamesToCommandText.get(fileName));
            Path currentUndo = Storage.getGameUndo(gameToUndo.getName(), fileName);
            if (!currentUndo.toFile().delete()) {
                BotLogger.error(new LogOrigin(gameToUndo), "Failed to delete undo file: " + currentUndo);
            }
        }

        runAfterUndo(
                gameToUndo,
                "post to the undo log",
                () -> sendUndoConfirmationMessage(gameToUndo, undoIndex, latestUndoIndex, undoCommands));
    }

    private static void sendUndoConfirmationMessage(
            Game game, int undoIndex, int latestUndoIndex, List<String> undoCommands) {
        StringBuilder sb = new StringBuilder("Rolled back to save `")
                .append(undoIndex)
                .append("` from `")
                .append(latestUndoIndex)
                .append("`:\n");

        String gameName = game.getName();
        for (int i = 0; i < undoCommands.size(); i++) {
            sb.append("> `")
                    .append(latestUndoIndex - i)
                    .append("` ")
                    .append(undoCommands.get(i))
                    .append('\n');
        }
        ButtonHelper.findOrCreateThreadWithMessage(game, gameName + "-undo-log", sb.toString());
    }

    private static void generateSavedButtons(Game game) {
        try {
            if (!game.getSavedButtons().isEmpty()
                    && game.getSavedChannel() != null
                    && !game.getPhaseOfGame().contains("status")) {
                MessageHelper.sendMessageToChannelWithButtons(
                        game.getSavedChannel(), game.getSavedMessage(), ButtonHelper.getSavedButtons(game));
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Error trying to generated saved buttons for " + game.getName(), e);
        }
    }

    private static void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            BotLogger.error("Error trying to delete file: " + path, e);
        }
    }

    static Game loadUndoForMissingGame(String gameName) {
        List<Integer> sortedUndoNumbers = GameUndoNameService.getSortedUndoNumbers(gameName);
        if (sortedUndoNumbers.isEmpty()) {
            BotLogger.warning("Attempted to load undo for missing game, but one did not exist: " + gameName);
            return null;
        }
        int latestUndoIndex = sortedUndoNumbers.getLast();
        File currentGameFile = Storage.getGameFile(gameName + Constants.TXT);
        try {
            replaceGameFileWithUndo(gameName, latestUndoIndex, currentGameFile.toPath());
            Game loadedGame = GameLoadService.load(gameName);
            if (loadedGame != null) {
                GameWebStatePipeline.queue(loadedGame);
            }
            return loadedGame;
        } catch (IOException e) {
            BotLogger.error("Error trying to undo for missing game: " + gameName, e);
        }
        return null;
    }
}
