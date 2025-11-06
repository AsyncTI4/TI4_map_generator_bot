package ti4.cron;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.helpers.TimedRunnable;

@UtilityClass
public class CronManager {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static final Map<String, Runnable> CRONS = new ConcurrentHashMap<>();
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;

    public static void schedulePeriodically(
            Class<?> clazz, Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        CRONS.put(clazz.getSimpleName(), runnable);
        TimedRunnable timedRunnable = new TimedRunnable(clazz.getSimpleName(), runnable);
        SCHEDULER.scheduleAtFixedRate(timedRunnable, initialDelay, period, unit);
    }

    public static void scheduleOnce(Class<?> clazz, Runnable runnable, long initialDelay, TimeUnit unit) {
        CRONS.put(clazz.getSimpleName(), runnable);
        TimedRunnable timedRunnable = new TimedRunnable(clazz.getSimpleName(), runnable);
        SCHEDULER.schedule(timedRunnable, initialDelay, unit);
    }

    public static void schedulePeriodicallyAtTime(
            Class<?> clazz, Runnable runnable, int hour, int minute, ZoneId zoneId) {
        CRONS.put(clazz.getSimpleName(), runnable);
        long initialDelaySeconds = calculateInitialDelaySeconds(hour, minute, zoneId);
        long periodSeconds = TimeUnit.DAYS.toSeconds(1);
        schedulePeriodically(clazz, runnable, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
    }

    private static long calculateInitialDelaySeconds(int hour, int minute, ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime nextRun =
                now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return nextRun.toEpochSecond() - now.toEpochSecond();
    }

    public static boolean runCron(String cronName) {
        Runnable runnable = CRONS.get(cronName);
        if (runnable == null) {
            return false;
        }
        var timedRunnable = new TimedRunnable(cronName, runnable);
        SCHEDULER.execute(timedRunnable);
        return true;
    }

    public static Set<String> getCronNames() {
        return CRONS.keySet();
    }

    public static void shutdown() {
        SCHEDULER.shutdown();
        try {
            if (!SCHEDULER.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            SCHEDULER.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
