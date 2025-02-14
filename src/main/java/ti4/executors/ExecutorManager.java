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

    private static final ExecutorService EXECUTOR_SERVICE =
            Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    private static final Set<String> gameExecutions = ConcurrentHashMap.newKeySet();

    public static void runAsync(String name, String gameName, MessageChannel messageChannel, Runnable runnable) {
        if (canExecuteGameCommand(gameName, messageChannel)) {
            runAsync(name, wrapWithGameRelease(gameName, runnable));
        }
    }

    private static boolean canExecuteGameCommand(String gameName, MessageChannel messageChannel) {
        if (GameManager.isValid(gameName) && !gameExecutions.add(gameName)) {
            MessageHelper.sendMessageToChannel(
                    messageChannel, "The bot hasn't finished processing the last command for this game. Please wait.");
            return false;
        }
        return true;
    }

    private static Runnable wrapWithGameRelease(String gameName, Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } finally {
                gameExecutions.remove(gameName);
            }
        };
    }

    public static void runAsync(
            String name,
            String gameName,
            MessageChannel messageChannel,
            int executionTimeWarningThresholdSeconds,
            Runnable runnable) {
        if (canExecuteGameCommand(gameName, messageChannel)) {
            runAsync(name, executionTimeWarningThresholdSeconds, wrapWithGameRelease(gameName, runnable));
        }
    }

    public static void runAsync(String name, Runnable runnable) {
        var timedRunnable = new TimedRunnable(name, runnable);
        EXECUTOR_SERVICE.execute(timedRunnable);
    }

    public static void runAsync(String name, int executionTimeWarningThresholdSeconds, Runnable runnable) {
        var timedRunnable = new TimedRunnable(name, executionTimeWarningThresholdSeconds, runnable);
        EXECUTOR_SERVICE.execute(timedRunnable);
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
