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
        CronManager.register(OldUndoFileCleanupCron.class, OldUndoFileCleanupCron::cleanup, 3, 0, ZoneId.of("America/New_York"));
    }

    private static void cleanup() {
        try {
            for (Game game : GameManager.getGameNameToGame().values()) {
                GameSaveLoadManager.cleanUpExcessUndoFilesAndReturnLatestIndex(game);
            }
            GameSaveLoadManager.cleanupOldUndoFiles();
        } catch (Exception e) {
            BotLogger.log("**OldUndoFileCleanupCron failed.**", e);
        }
        BotLogger.log("Ran OldUndoFileCleanupCron.");
    }
}
