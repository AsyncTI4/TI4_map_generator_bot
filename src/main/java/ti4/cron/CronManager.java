package ti4.cron;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutorUtility;
import ti4.executors.ShutdownResult;
import ti4.helpers.TimedRunnable;
import ti4.logging.BotLogger;

@UtilityClass
public class CronManager {

    private static final ScheduledThreadPoolExecutor SCHEDULER = new ScheduledThreadPoolExecutor(1);
    private static final Map<String, Runnable> CRONS = new ConcurrentHashMap<>();
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    public static ScheduledFuture<?> schedulePeriodically(
            Class<?> clazz, Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        CRONS.put(clazz.getSimpleName(), runnable);
        Runnable cronTask = nonThrowingCronTask(clazz.getSimpleName(), runnable);
        return SCHEDULER.scheduleAtFixedRate(cronTask, initialDelay, period, unit);
    }

    public static void scheduleOnce(Class<?> clazz, Runnable runnable, long initialDelay, TimeUnit unit) {
        CRONS.put(clazz.getSimpleName(), runnable);
        Runnable cronTask = nonThrowingCronTask(clazz.getSimpleName(), runnable);
        SCHEDULER.schedule(cronTask, initialDelay, unit);
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
        SCHEDULER.execute(nonThrowingCronTask(cronName, runnable));
        return true;
    }

    public static Set<String> getCronNames() {
        return CRONS.keySet();
    }

    public static ShutdownResult shutdown() {
        return ExecutorUtility.shutdownAndAwaitTermination(SCHEDULER, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static Runnable nonThrowingCronTask(String cronName, Runnable runnable) {
        TimedRunnable timedRunnable = new TimedRunnable(cronName, runnable);
        return () -> {
            try {
                timedRunnable.run();
            } catch (Throwable t) {
                BotLogger.error("Unhandled exception in cron: " + cronName, t);
            }
        };
    }
}
