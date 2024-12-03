package ti4.cron;

import java.time.ZoneId;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.BotLogger;

@UtilityClass
public class OldUndoFileCleanupCron {

    public static void register() {
        CronManager.register(UploadStatsCron.class, OldUndoFileCleanupCron::cleanup, 3, 0, ZoneId.of("America/New_York"));
    }

    private static void cleanup() {
        for (Game game : GameManager.getGameNameToGame().values()) {
            GameSaveLoadManager.cleanUpExcessUndoFilesAndReturnLatestIndex(game);
        }
        BotLogger.log("Cleaned excess undo files.");
        GameSaveLoadManager.cleanupOldUndoFiles();
        BotLogger.log("Cleaned old undo files.");
    }
}
