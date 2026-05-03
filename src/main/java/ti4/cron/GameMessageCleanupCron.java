package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.message.GameMessageManager;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class GameMessageCleanupCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                GameMessageCleanupCron.class, GameMessageCleanupCron::cleanup, 4, 0, ZoneId.of("America/New_York"));
    }

    private static void cleanup() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running GameMessageCleanupCron.");
        try {
            GameMessageManager.cleanupStaleEntries();
        } catch (Exception e) {
            BotLogger.error("**GameMessageCleanupCron failed.**", e);
        }
        BotLogger.logCron("Finished GameMessageCleanupCron.");
    }
}
