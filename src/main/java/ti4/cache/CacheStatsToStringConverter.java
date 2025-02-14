package ti4.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ToStringHelper;

@UtilityClass
public class CacheStatsToStringConverter {

    private static final ThreadLocal<DecimalFormat> percentFormatter =
            ThreadLocal.withInitial(() -> new DecimalFormat("##.##%"));
    private static final ThreadLocal<DecimalFormat> twoDecimalsFormatter =
            ThreadLocal.withInitial(() -> new DecimalFormat("##.##"));

    public static String convert(Set<Map.Entry<String, Cache<?, ?>>> namesToCaches) {
        return namesToCaches.stream()
                .map(entry -> convert(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n\n"));
    }

    public static String convert(String name, Cache<?, ?> cache) {
        CacheStats stats = cache.stats();
        return ToStringHelper.of(name)
                .add("liveTime", getLiveTime())
                .add("hitCount", stats.hitCount())
                .add("hitRate", percentFormatter.get().format(stats.hitRate()))
                .add("loadCount", stats.loadCount())
                .add("loadFailureCount", stats.loadFailureCount())
                .add("averageLoadPenaltyMilliseconds", nanosecondsToMilliseconds(stats.averageLoadPenalty()))
                .add("evictionCount", stats.evictionCount())
                .add("currentSize", cache.estimatedSize())
                .toString();
    }

    private static String nanosecondsToMilliseconds(double nanoseconds) {
        return twoDecimalsFormatter.get().format(nanoseconds / 1_000_000);
    }

    private static String getLiveTime() {
        long millisecondsSinceBotStarted = System.currentTimeMillis() - AsyncTI4DiscordBot.START_TIME_MILLISECONDS;
        long liveTimeHours = TimeUnit.HOURS.convert(millisecondsSinceBotStarted, TimeUnit.MILLISECONDS);
        long liveTimeMinutes =
                TimeUnit.MINUTES.convert(millisecondsSinceBotStarted, TimeUnit.MILLISECONDS) - liveTimeHours * 60;
        return liveTimeHours + "h" + liveTimeMinutes + "m";
    }
}
