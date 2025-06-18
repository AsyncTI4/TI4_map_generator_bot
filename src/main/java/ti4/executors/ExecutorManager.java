package ti4.executors;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.helpers.TimedRunnable;
import ti4.map.manage.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class ExecutorManager {

    private static final int TASK_TIMEOUT_SECONDS = 30;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newVirtualThreadPerTaskExecutor();
    private static final Set<String> executionLocks = ConcurrentHashMap.newKeySet();

    public static void runAsync(String name, String gameName, MessageChannel messageChannel, Runnable runnable) {
        if (CircuitBreaker.isOpen()) {
            Duration durationUntilCircuitCloses = CircuitBreaker.getDurationUtilClose();
            MessageHelper.sendMessageToChannel(messageChannel, "The bot is taking a breather. Try again in " +
                durationUntilCircuitCloses.toMinutes() + " minutes and " + durationUntilCircuitCloses.getSeconds() % 60 + " seconds.");
            return;
        }
        if (!canExecuteGameCommand(gameName, messageChannel)) {
            return;
        }
        Runnable onTimeout = () -> {
            MessageHelper.sendMessageToChannel(messageChannel, "The last command timed out. Wait a few minutes and double check the map state.");
            executionLocks.remove(gameName);
        };
        var lockReleaseRunnable = wrapWithLockRelease(gameName, runnable);
        var timedRunnable = new TimedRunnable(name, lockReleaseRunnable);
        runAsync(name, timedRunnable, onTimeout);
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
        runAsync(name, wrapWithLockRelease(name, runnable));
    }


    public static void runAsync(String name, Runnable runnable) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var timedRunnable = new TimedRunnable(name, runnable);
        runAsync(name, timedRunnable);
    }

    public static void runAsync(String name, int executionTimeWarningThresholdSeconds, Runnable runnable) {
        if (CircuitBreaker.isOpen()) {
            return;
        }
        var timedRunnable = new TimedRunnable(name, executionTimeWarningThresholdSeconds, runnable);
        runAsync(name, timedRunnable);
    }

    private static void runAsync(String name, TimedRunnable runnable) {
        runAsync(name, runnable, null);
    }

    private static void runAsync(String name, TimedRunnable runnable, Runnable onTimeout) {
        CompletableFuture.runAsync(runnable, EXECUTOR_SERVICE)
            .orTimeout(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete((result, exception) -> {
                // This does not cancel the task, just reports that it exceeded the timeout threshold
                if (exception instanceof TimeoutException) {
                    CircuitBreaker.incrementThresholdCount();
                    BotLogger.error("Timeout occurred during task: " + name, exception);
                    if (onTimeout != null) {
                        onTimeout.run();
                    }
                } else if (exception != null) {
                    BotLogger.error("Error occurred during task: " + name, exception);
                }
            });
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
