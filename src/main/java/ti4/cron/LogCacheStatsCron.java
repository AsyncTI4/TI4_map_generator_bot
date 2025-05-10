package ti4.cron;

import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.cache.CacheManager;
import ti4.cache.CacheStatsToStringConverter;
import ti4.message.BotLogger;

@UtilityClass
public class LogCacheStatsCron {

    public static void register() {
        CronManager.schedulePeriodically(LogCacheStatsCron.class, LogCacheStatsCron::logCacheStats, 1, 4, TimeUnit.HOURS);
    }

    private static void logCacheStats() {
        try {
            String cacheStats = CacheStatsToStringConverter.convert(CacheManager.getNamesToCaches());
            BotLogger.info("```\n" + cacheStats + "\n```");
        } catch (Exception e) {
            BotLogger.error("**LogCacheStatsCron failed.**", e);
        }
    }
}
