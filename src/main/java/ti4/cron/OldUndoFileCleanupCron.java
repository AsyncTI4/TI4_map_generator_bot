package ti4.cron;

import java.time.ZoneId;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.BotLogger;

@UtilityClass
class OldUndoFileCleanupCron {

    static {
        CronManager.register(OldUndoFileCleanupCron.class, OldUndoFileCleanupCron::cleanup, 3, 0, ZoneId.of("America/New_York"));
    }

    private static void cleanup() {
        BotLogger.logWithTimestamp("Cleaning up excess undo files...");
        try {
            for (Game game : GameManager.getGameNameToGame().values()) {
                GameSaveLoadManager.cleanUpExcessUndoFilesAndReturnLatestIndex(game);
            }
            BotLogger.logWithTimestamp("Cleaned excess undo files, starting on old undo files...");

            GameSaveLoadManager.cleanupOldUndoFiles();
        } catch (Exception e) {
            BotLogger.log("**Error cleaning up old undo files!**", e);
        }
        BotLogger.logWithTimestamp("Cleaned old undo files.");
    }
}
