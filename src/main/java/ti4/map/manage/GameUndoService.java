package ti4.map.manage;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.GameUndoNameService;
import ti4.service.info.CardsInfoService;

@UtilityClass
class GameUndoService {

    public static void createUndoCopy(String gameName) {
        int latestIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(gameName);
        if (latestIndex < 0) return;
        File gameFile = Storage.getGameFile(gameName + Constants.TXT);
        if (!gameFile.exists()) return;
        try {
            File mapUndoStorage = Storage.getGameUndoStorage(getUndoFileName(gameName, latestIndex + 1));
            Files.copy(gameFile.toPath(), mapUndoStorage.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            BotLogger.log("Error copying undo file for " + gameName, e);
        }
    }

    private static int cleanUpExcessUndoFilesAndReturnLatestIndex(String gameName) {
        String gameNameFileNamePrefix = gameName + "_";
        var gameUndoPath = Storage.getGameUndoDirectory().toPath();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameUndoPath, gameNameFileNamePrefix + "*")) {
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
            int maxUndoFilesPerGame = GameManager.getManagedGame(gameName).isHasEnded() ? 10 : 100;
            int oldestUndoNumberThatShouldExist = maxUndoNumber - maxUndoFilesPerGame;

            undoNumbers.stream()
                .filter(undoIndex -> undoIndex < oldestUndoNumberThatShouldExist)
                .map(undoIndex -> getUndoFileName(gameName, undoIndex))
                .forEach(fileName -> deleteFile(Storage.getGameUndoStoragePath(fileName)));

            return maxUndoNumber;
        } catch (Exception e) {
            BotLogger.log("Error trying clean up excess undo files for: " + gameName, e);
        }
        return -1;
    }

    @Nullable
    public static Game undo(Game game) {
        int latestUndoIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(game.getName());
        return undo(game, latestUndoIndex - 1, latestUndoIndex);
    }

    @Nullable
    public static Game undo(Game game, int undoIndex) {
        if (undoIndex <= 0) return null;
        int latestUndoIndex = cleanUpExcessUndoFilesAndReturnLatestIndex(game.getName());
        return undo(game, undoIndex, latestUndoIndex);
    }

    private static Game undo(Game game, int undoIndex, int latestUndoIndex) {
        if (latestUndoIndex <= 1) return null;
        String gameName = game.getName();
        try {
            File currentGameFile = Storage.getGameFile(gameName + Constants.TXT);
            if (!currentGameFile.exists()) {
                BotLogger.log("Game file for " + gameName + " doesn't exist!");
                return null;

            }

            replaceGameFileWithUndo(gameName, undoIndex, currentGameFile.toPath());
            Game loadedGame = GameLoadService.load(gameName);
            if (loadedGame == null) { // rollback if we failed to load the undo
                replaceGameFileWithUndo(gameName, latestUndoIndex, currentGameFile.toPath());
                return null;
            }

            generateSavedButtons(loadedGame);

            for (Player p1 : loadedGame.getRealPlayers()) {
                Player p2 = game.getPlayerFromColorOrFaction(p1.getFaction());
                if (p2 != null && (p1.getAc() != p2.getAc() || p1.getSo() != p2.getSo())) {
                    CardsInfoService.sendCardsInfo(loadedGame, p1);
                }
            }

            Map<String, String> undoNamesToCommandText = GameUndoNameService.getUndoNamesToCommandText(game, latestUndoIndex - undoIndex);
            List<String> undoCommands = new ArrayList<>();
            for (int i = latestUndoIndex; i > undoIndex; i--) {
                String fileName = getUndoFileName(gameName, i);
                File currentUndo = Storage.getGameUndoStorage(fileName);
                if (!currentUndo.delete()) {
                    BotLogger.log("Failed to delete undo file: " + currentUndo.getAbsolutePath());
                } else {
                    undoCommands.add(undoNamesToCommandText.get(fileName));
                }
            }

            if (!game.isFowMode()) {
                sendUndoConfirmationMessage(game, undoIndex, latestUndoIndex, undoCommands);
            }
            return loadedGame;
        } catch (Exception e) {
            BotLogger.log("Error trying to undo: " + gameName, e);
            return null;
        }
    }

    private static void replaceGameFileWithUndo(String gameName, int undoIndex, Path gameFilePath) throws IOException {
        File undoFile = Storage.getGameUndoStorage(getUndoFileName(gameName, undoIndex));
        Files.copy(undoFile.toPath(), gameFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String getUndoFileName(String gameName, int undoIndex) {
        return gameName + "_" + undoIndex + Constants.TXT;
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
            BotLogger.log("Error trying to generated saved buttons for " + game.getName(), e);
        }
    }

    private static void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            BotLogger.log("Error trying to delete file: " + path, e);
        }
    }
}
