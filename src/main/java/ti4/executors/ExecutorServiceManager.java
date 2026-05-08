package ti4.executors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.helpers.TimedRunnable;

@UtilityClass
public class ExecutorServiceManager {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 60;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("ti4-worker-", 0).factory());

    public static void runAsyncWithLock(
            String name, String gameName, MessageChannel messageChannel, Runnable runnable) {
        runAsyncWithLock(name, gameName, messageChannel, runnable, ExecutionLockType.WRITE);
    }

    public static void runAsyncWithLock(
            String taskName,
            String lockName,
            MessageChannel messageChannel,
            Runnable runnable,
            ExecutionLockType lockType) {
        if (CircuitBreaker.checkIsOpenAndPostWarningIfTrue(messageChannel)) {
            return;
        }

        if (isNotBlank(lockName)) {
            runnable = ExecutionLockManager.wrapWithTryLockAndRelease(lockName, lockType, runnable, messageChannel);
        }

        var timedRunnable = new TimedRunnable(taskName, runnable);
        runAsync(timedRunnable);
    }

    public static void runAsyncWithLock(String taskName, Runnable runnable) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var lockReleaseRunnable =
                ExecutionLockManager.wrapWithTryLockAndRelease(taskName, ExecutionLockType.WRITE, runnable);
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

    public static void runAsyncBypassCircuitBreaker(String name, Runnable runnable) {
        var timedRunnable = new TimedRunnable(name, runnable);
        runAsync(timedRunnable);
    }

    public static void runAsyncWithLockBypassCircuitBreaker(
            String taskName,
            String lockName,
            MessageChannel messageChannel,
            Runnable runnable,
            ExecutionLockType lockType) {
        if (isNotBlank(lockName)) {
            runnable = ExecutionLockManager.wrapWithTryLockAndRelease(lockName, lockType, runnable, messageChannel);
        }
        var timedRunnable = new TimedRunnable(taskName, runnable);
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

    public static ShutdownResult shutdown() {
        return ExecutorUtility.shutdownAndAwaitTermination(
                EXECUTOR_SERVICE, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
