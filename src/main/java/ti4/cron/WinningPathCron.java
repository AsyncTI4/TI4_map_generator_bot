package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.service.statistics.game.WinningPathPersistenceService;

@UtilityClass
public class WinningPathCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                WinningPathCron.class, WinningPathCron::recompute, 4, 0, ZoneId.of("America/New_York"));
    }

    private static void recompute() {
        BotLogger.info("Running WinningPathCron.");
        try {
            WinningPathPersistenceService.recomputeFile();
        } catch (Exception e) {
            BotLogger.error("**WinningPathCron failed.**", e);
        }
        BotLogger.info("Finished WinningPathCron.");
    }
}

