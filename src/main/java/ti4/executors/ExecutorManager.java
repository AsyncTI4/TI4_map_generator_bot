package ti4.executors;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.helpers.TimedRunnable;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;

@UtilityClass
public class ExecutorManager {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newVirtualThreadPerTaskExecutor();
    private static final Set<String> executionLocks = ConcurrentHashMap.newKeySet();

    public static void runAsync(String name, String gameName, MessageChannel messageChannel, Runnable runnable) {
        if (CircuitBreaker.checkIsOpenAndPostWarningIfTrue(messageChannel)) {
            return;
        }
        if (!canExecuteGameCommand(gameName, messageChannel)) {
            return;
        }
        var lockReleaseRunnable = wrapWithLockRelease(gameName, runnable);
        var timedRunnable = new TimedRunnable(name, lockReleaseRunnable);
        runAsync(timedRunnable);
    }

    private static boolean canExecuteGameCommand(String gameName, MessageChannel messageChannel) {
        if (GameManager.isValid(gameName) && !executionLocks.add(gameName)) {
            MessageHelper.sendMessageToChannel(messageChannel, "The bot hasn't finished processing the last command for this game. Please wait.");
            return false;
        }
        return true;
    }

    private static Runnable wrapWithLockRelease(String gameName, Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } finally {
                executionLocks.remove(gameName);
            }
        };
    }

    public static void runAsyncIfNotRunning(String name, Runnable runnable) {
        if (CircuitBreaker.isOpen() || !executionLocks.add(name)) {
            return;
        }
        var lockReleaseRunnable = wrapWithLockRelease(name, runnable);
        var timedRunnable = new TimedRunnable(name, lockReleaseRunnable);
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
