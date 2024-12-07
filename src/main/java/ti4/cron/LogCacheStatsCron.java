package ti4.cron;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.experimental.UtilityClass;
import ti4.AsyncTI4DiscordBot;
import ti4.cache.CacheManager;
import ti4.helpers.ToStringHelper;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class LogCacheStatsCron {

    private static final ThreadLocal<DecimalFormat> percentFormatter = ThreadLocal.withInitial(() -> new DecimalFormat("##.##%"));

    public static void register() {
        CronManager.register(LogCacheStatsCron.class, LogCacheStatsCron::logCacheStats, 1, 4, TimeUnit.HOURS);
    }

    private static void logCacheStats() {
        try {
            var cacheStats = CacheManager.getNamesToCaches().stream()
                .map(entry -> cacheStatsToString(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n\n"));
            MessageHelper.sendMessageToPrimaryBotLogChannel("```\n" + cacheStats + "\n```");
        } catch (Exception e) {
            BotLogger.log("**LogCacheStatsCron failed.**", e);
        }
    }

    private static String cacheStatsToString(String name, Cache<?, ?> cache) {
        CacheStats stats = cache.stats();
        return ToStringHelper.of(name)
                .add("liveTime", getLiveTime())
                .add("hitCount", stats.hitCount())
                .add("hitRate", formatPercent(stats.hitRate()))
                .add("loadCount", stats.loadCount())
                .add("loadFailureCount", stats.loadFailureCount())
                .add("averageLoadPenaltyMilliseconds", TimeUnit.MILLISECONDS.convert((long) stats.averageLoadPenalty(), TimeUnit.NANOSECONDS))
                .add("evictionCount", stats.evictionCount())
                .add("currentSize", cache.estimatedSize())
                .toString();
    }

    private static String getLiveTime() {
        long millisecondsSinceBotStarted = System.currentTimeMillis() - AsyncTI4DiscordBot.START_TIME_MILLISECONDS;
        long liveTimeHours = TimeUnit.HOURS.convert(millisecondsSinceBotStarted, TimeUnit.MILLISECONDS);
        long liveTimeMinutes = TimeUnit.MINUTES.convert(millisecondsSinceBotStarted, TimeUnit.MILLISECONDS) - liveTimeHours * 60;
        return liveTimeHours + "h" + liveTimeMinutes + "m";
    }

    private static String formatPercent(double d) {
        return percentFormatter.get().format(d);
    }
}
