package ti4.game.persistence;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import ti4.logging.BotLogger;

class GameDatabaseReconciliationChain {

    private static final int START_AND_FINISH_WARNING_THRESHOLD_SECONDS = 60;
    private static final int GAME_WARNING_THRESHOLD_SECONDS = 5;

    private final String taskName;
    private final TaskQueue taskQueue;
    private final BooleanSupplier maintenanceModeOn;
    private boolean running;
    private Supplier<Run> pendingRun;

    GameDatabaseReconciliationChain(String taskName, TaskQueue taskQueue, BooleanSupplier maintenanceModeOn) {
        this.taskName = taskName;
        this.taskQueue = taskQueue;
        this.maintenanceModeOn = maintenanceModeOn;
    }

    synchronized boolean start(Supplier<Run> newRun) {
        if (running) {
            BotLogger.info(taskName + " is already running, so the request to start another run was ignored.");
            return false;
        }
        running = true;
        boolean queued = queue(" start", START_AND_FINISH_WARNING_THRESHOLD_SECONDS, () -> {
            Run run = newRun.get();
            return queueGame(run, run.start(), 0);
        });
        if (!queued) running = false;
        return queued;
    }

    synchronized boolean startAfterCurrentRun(Supplier<Run> newRun) {
        if (running) {
            pendingRun = newRun;
            return true;
        }
        return start(newRun);
    }

    private synchronized void complete() {
        running = false;
        Supplier<Run> nextRun = pendingRun;
        pendingRun = null;
        if (nextRun != null) start(nextRun);
    }

    synchronized boolean isRunning() {
        return running;
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
            if (!queuedNext) complete();
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
