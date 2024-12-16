package ti4.executors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.helpers.TimedRunnable;
import ti4.map.manage.GameManager;

@UtilityClass
public class ExecutorManager {

    private static final List<ExecutorService> EXECUTORS;
    static {
        List<ExecutorService> executors = new ArrayList<>();
        int numberOfThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < numberOfThreads; i++) {
            executors.add(Executors.newSingleThreadScheduledExecutor());
        }
        EXECUTORS = List.copyOf(executors);
    }

    private static void runAsync(ExecutorService executor, String name, Runnable runnable) {
        var timedRunnable = new TimedRunnable(name, runnable);
        executor.execute(timedRunnable);
    }

    private static void runAsync(ExecutorService executor, String name, int executionTimeWarningThresholdSeconds, Runnable runnable) {
        var timedRunnable = new TimedRunnable(name, executionTimeWarningThresholdSeconds, runnable);
        executor.execute(timedRunnable);
    }

    public static void runAsync(String name, String gameName, Runnable runnable) {
        if (!GameManager.isValid(gameName)) {
            runAsync(name, runnable);
            return;
        }
        runAsync(getExecutor(gameName), name, runnable);
    }

    private static ExecutorService getExecutor(String gameName) {
        int index = Math.abs(gameName.hashCode()) % EXECUTORS.size();
        return EXECUTORS.get(index);
    }

    public static void runAsync(String name, String gameName, int executionTimeWarningThresholdSeconds, Runnable runnable) {
        if (gameName == null) {
            runAsync(name, executionTimeWarningThresholdSeconds, runnable);
            return;
        }
        runAsync(getExecutor(gameName), name, executionTimeWarningThresholdSeconds, runnable);
    }

    public static void runAsync(String name, Runnable runnable) {
        runAsync(getRandomExecutor(), name, runnable);
    }

    private static ExecutorService getRandomExecutor() {
        int random = ThreadLocalRandom.current().nextInt(EXECUTORS.size());
        return EXECUTORS.get(random);
    }

    public static void runAsync(String name, int executionTimeWarningThresholdSeconds, Runnable runnable) {
        runAsync(getRandomExecutor(), name, executionTimeWarningThresholdSeconds, runnable);
    }

    public static boolean shutdown() {
        for (ExecutorService executor : EXECUTORS) {
            executor.shutdown();
        }
        boolean shutdown = true;
        for (ExecutorService executor : EXECUTORS) {
            try {
                if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    shutdown = false;
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                shutdown = false;
            }
        }
        return shutdown;
    }
}
