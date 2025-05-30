package ti4.executors;

import java.util.Set;
import java.util.concurrent.CancellationException;
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

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    private static final Set<String> executionLocks = ConcurrentHashMap.newKeySet();

    public static void runAsync(String name, String gameName, MessageChannel messageChannel, Runnable runnable) {
        if (canExecuteGameCommand(gameName, messageChannel)) {
            Runnable onCancel = () -> {
                MessageHelper.sendMessageToChannel(messageChannel, "The last command timed out and was cancelled. Double check the map state " +
                    "and use undo if the command partially completed.");
                executionLocks.remove(gameName);
            };
            runAsync(name, wrapWithLockRelease(gameName, runnable), onCancel);
        }
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
        if (!executionLocks.add(name)) {
            return;
        }
        runAsync(name, wrapWithLockRelease(name, runnable));
    }


    public static void runAsync(String name, Runnable runnable) {
        var timedRunnable = new TimedRunnable(name, runnable);
        runAsync(name, timedRunnable);
    }

    public static void runAsync(String name, int executionTimeWarningThresholdSeconds, Runnable runnable) {
        var timedRunnable = new TimedRunnable(name, executionTimeWarningThresholdSeconds, runnable);
        runAsync(name, timedRunnable);
    }

    private static void runAsync(String name, TimedRunnable runnable) {
        runAsync(name, runnable, null);
    }

    private static void runAsync(String name, Runnable runnable, Runnable onCancel) {
        var timedRunnable = new TimedRunnable(name, runnable);
        CompletableFuture
            .runAsync(timedRunnable, EXECUTOR_SERVICE)
            .orTimeout(30, TimeUnit.SECONDS)
            .whenComplete((result, exception) -> {
                if (exception instanceof TimeoutException || exception instanceof CancellationException) {
                    BotLogger.error("Timed out while waiting for async task to finish. Canceled. Task name: " + name, exception);
                    if (onCancel != null) {
                        onCancel.run();
                    }
                } else if (exception != null) {
                    BotLogger.error("Async task finished with an error. Task name: " + name, exception);
                }
            });
    }

    public static boolean shutdown() {
        EXECUTOR_SERVICE.shutdown();
        try {
            if (!EXECUTOR_SERVICE.awaitTermination(20, TimeUnit.SECONDS)) {
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
