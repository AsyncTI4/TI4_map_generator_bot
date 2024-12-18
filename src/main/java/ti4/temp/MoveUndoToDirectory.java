package ti4.temp;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;

@UtilityClass
public class MoveUndoToDirectory {

    public static void moveUndoFilesToDirectories() {
        BotLogger.logWithTimestamp(" Moving undo files...");
        for (ManagedGame managedGame : GameManager.getManagedGames()) {
            String gameName = managedGame.getName();
            moveUndoFilesToDirectory(gameName);
        }
        BotLogger.logWithTimestamp(" Finished moving undo files.");
    }

    private static void moveUndoFilesToDirectory(String gameName) {
        var gameUndoPath = Storage.getBaseGameUndoDirectory();
        var currentGameUndoDirectoryPath = gameUndoPath.resolve(gameName);
        try {
            Files.createDirectories(currentGameUndoDirectoryPath);
        } catch (IOException e) {
            BotLogger.log("Error creating directory: " + currentGameUndoDirectoryPath, e);
            return;
        }

        String gameNameFileNamePrefix = gameName + "_";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameUndoPath, gameNameFileNamePrefix + "*")) {
            for (Path path : stream) {
                try {
                    Path targetPath = currentGameUndoDirectoryPath.resolve(path.getFileName());
                    Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    BotLogger.log("Error moving file: " + path + " to " + currentGameUndoDirectoryPath, e);
                }
            }
        } catch (Exception e) {
            BotLogger.log("Error trying to move undo files for: " + gameName, e);
        }
    }
}
