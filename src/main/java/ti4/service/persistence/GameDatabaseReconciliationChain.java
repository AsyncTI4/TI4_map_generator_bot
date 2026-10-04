package ti4.service.persistence;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import ti4.logging.BotLogger;

class GameDatabaseReconciliationChain {

    private static final int START_AND_FINISH_WARNING_THRESHOLD_SECONDS = 60;
    private static final int GAME_WARNING_THRESHOLD_SECONDS = 5;

    private final String taskName;
    private final TaskQueue taskQueue;
    private final BooleanSupplier maintenanceModeOn;
    private final AtomicBoolean running = new AtomicBoolean();

    GameDatabaseReconciliationChain(String taskName, TaskQueue taskQueue, BooleanSupplier maintenanceModeOn) {
        this.taskName = taskName;
        this.taskQueue = taskQueue;
        this.maintenanceModeOn = maintenanceModeOn;
    }

    boolean start(Supplier<Run> newRun) {
        if (!running.compareAndSet(false, true)) {
            BotLogger.info(taskName + " is already running, so the request to start another run was ignored.");
            return false;
        }
        boolean queued = queue(" start", START_AND_FINISH_WARNING_THRESHOLD_SECONDS, () -> {
            Run run = newRun.get();
            return queueGame(run, run.start(), 0);
        });
        if (!queued) running.set(false);
        return queued;
    }

    boolean isRunning() {
        return running.get();
    }

    private boolean queueGame(Run run, List<String> gameNames, int index) {
        if (index >= gameNames.size()) {
            return queue(" finish", START_AND_FINISH_WARNING_THRESHOLD_SECONDS, () -> {
                run.finish();
                return false;
            });
        }
        String gameName = gameNames.get(index);
        return queue(" for `" + gameName + "`", GAME_WARNING_THRESHOLD_SECONDS, () -> {
            run.reconcile(gameName);
            return queueGame(run, gameNames, index + 1);
        });
    }

    private boolean queue(String taskSuffix, int warningThresholdSeconds, Link link) {
        return taskQueue.queue(taskName + taskSuffix, warningThresholdSeconds, () -> runUnlessStopped(link));
    }

    private void runUnlessStopped(Link link) {
        boolean queuedNext = false;
        try {
            if (Thread.currentThread().isInterrupted()) {
                BotLogger.warning(taskName + " was interrupted before it finished.");
                return;
            }
            if (maintenanceModeOn.getAsBoolean()) {
                BotLogger.warning(taskName + " stopped because database maintenance mode was turned on.");
                return;
            }
            queuedNext = link.runAndQueueNext();
        } catch (Exception e) {
            BotLogger.error("**" + taskName + " failed.**", e);
        } finally {
            if (!queuedNext) running.set(false);
        }
    }

    @FunctionalInterface
    interface TaskQueue {
        boolean queue(String taskName, int warningThresholdSeconds, Runnable task);
    }

    interface Run {
        List<String> start();

        void reconcile(String gameName);

        void finish();
    }

    @FunctionalInterface
    private interface Link {
        boolean runAndQueueNext();
    }
}
