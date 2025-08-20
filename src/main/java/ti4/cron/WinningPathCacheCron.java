package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.service.statistics.game.WinningPathCacheService;

@UtilityClass
public class WinningPathCacheCron {

    public static void register() {
        CronManager.schedulePeriodically(
                WinningPathCacheCron.class, WinningPathCacheCron::precompute, 5, 90, TimeUnit.MINUTES);
    }

    private static void precompute() {
        BotLogger.info("Running WinningPathCacheCron.");
        try {
            WinningPathCacheService.recomputeCache();
        } catch (Exception e) {
            BotLogger.error("**WinningPathCacheCron failed.**", e);
        }
        BotLogger.info("Finished WinningPathCacheCron.");
    }
}
