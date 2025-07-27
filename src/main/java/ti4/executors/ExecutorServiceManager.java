package ti4.executors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.helpers.TimedRunnable;

@UtilityClass
public class ExecutorServiceManager {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newVirtualThreadPerTaskExecutor();

    public static void runAsync(String name, String gameName, MessageChannel messageChannel, Runnable runnable) {
        if (CircuitBreaker.checkIsOpenAndPostWarningIfTrue(messageChannel)) {
            return;
        }

        // TODO: We can do read/write based on if it is a save command
        var lockReleaseRunnable = ExecutionLockManager.wrapWithTryLockAndRelease(
            gameName, ExecutionLockManager.LockType.WRITE, runnable, messageChannel);
        var timedRunnable = new TimedRunnable(name, lockReleaseRunnable);
        runAsync(timedRunnable);
    }

    public static void runAsyncIfNotRunning(String taskName, Runnable runnable) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var lockReleaseRunnable = ExecutionLockManager.wrapWithTryLockAndRelease(taskName, ExecutionLockManager.LockType.WRITE, runnable);
        var timedRunnable = new TimedRunnable(taskName, lockReleaseRunnable);
        runAsync(timedRunnable);
    }

    public static void runAsync(String name, Runnable runnable) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var timedRunnable = new TimedRunnable(name, runnable);
        runAsync(timedRunnable);
    }

    public static void runAsync(String name, int executionTimeWarningThresholdSeconds, Runnable runnable) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var timedRunnable = new TimedRunnable(name, executionTimeWarningThresholdSeconds, runnable);
        runAsync(timedRunnable);
    }

    private static void runAsync(TimedRunnable timedRunnable) {
        ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
    }

    public static boolean shutdown() {
        EXECUTOR_SERVICE.shutdown();
        try {
            if (!EXECUTOR_SERVICE.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                EXECUTOR_SERVICE.shutdownNow();
                return false;
            }
        } catch (InterruptedException e) {
            EXECUTOR_SERVICE.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
}
