package ti4.cron;

import lombok.experimental.UtilityClass;
import ti4.message.BotLogger;
import ti4.service.statistics.game.WinningPathCacheService;

import java.util.concurrent.TimeUnit;

@UtilityClass
public class WinningPathCacheCron {

    public static void register() {
        CronManager.schedulePeriodically(WinningPathCacheCron.class, WinningPathCacheCron::precompute, 5, 90, TimeUnit.MINUTES);
    }

    private static void precompute() {
        try {
            WinningPathCacheService.recomputeCache();
        } catch (Exception e) {
            BotLogger.error("**WinningPathCacheCron failed.**", e);
        }
    }
}
