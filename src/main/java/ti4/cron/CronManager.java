package ti4.cron;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.helpers.TimedRunnable;

@UtilityClass
public class CronManager {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public static void schedulePeriodically(Class<?> clazz, Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        TimedRunnable timedRunnable = new TimedRunnable(clazz.getSimpleName(), runnable);
        SCHEDULER.scheduleAtFixedRate(timedRunnable, initialDelay, period, unit);
    }

    public static void scheduleOnce(Class<?> clazz, Runnable runnable, long initialDelay, TimeUnit unit) {
        TimedRunnable timedRunnable = new TimedRunnable(clazz.getSimpleName(), runnable);
        SCHEDULER.schedule(timedRunnable, initialDelay, unit);
    }

    public static void schedulePeriodicallyAtTime(Class<?> clazz, Runnable runnable, int hour, int minute, ZoneId zoneId) {
        long initialDelaySeconds = calculateInitialDelaySeconds(hour, minute, zoneId);
        long periodSeconds = TimeUnit.DAYS.toSeconds(1);
        schedulePeriodically(clazz, runnable, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
    }

    private static long calculateInitialDelaySeconds(int hour, int minute, ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return nextRun.toEpochSecond() - now.toEpochSecond();
    }

    public static void shutdown() {
        SCHEDULER.shutdown();
        try {
            if (!SCHEDULER.awaitTermination(20, TimeUnit.SECONDS)) {
                SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            SCHEDULER.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
