package ti4.cron;

import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.BotLogger;

@UtilityClass
public class OldUndoFileCleanupCron {

    public static void register() {
        CronManager.register(OldUndoFileCleanupCron.class, OldUndoFileCleanupCron::cleanup, 1, 240, TimeUnit.MINUTES);
    }

    private static void cleanup() {
        BotLogger.logWithTimestamp("Cleaning up excess undo files...");
        for (Game game : GameManager.getGameNameToGame().values()) {
            GameSaveLoadManager.cleanUpExcessUndoFilesAndReturnLatestIndex(game);
        }
        BotLogger.logWithTimestamp("Cleaned excess undo files, starting on old undo files...");

        GameSaveLoadManager.cleanupOldUndoFiles();
        BotLogger.logWithTimestamp("Cleaned old undo files.");
    }
}
