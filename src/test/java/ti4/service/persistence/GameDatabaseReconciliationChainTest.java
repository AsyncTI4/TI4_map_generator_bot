package ti4.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GameDatabaseReconciliationChainTest {

    // Stands in for the single-thread sync executor: tasks run in FIFO order when the test drains it.
    private final Deque<Runnable> executor = new ArrayDeque<>();
    private final List<String> taskNames = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final Map<String, Runnable> onReconcile = new HashMap<>();
    private boolean maintenanceMode;
    private boolean shuttingDown;

    private final GameDatabaseReconciliationChain chain =
            new GameDatabaseReconciliationChain("TestReconciler", this::queueTask, () -> maintenanceMode);

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void visitsEveryGameInItsOwnTaskThenFinishes() {
        assertThat(chain.start(() -> new RecordingRun(List.of("pbd3", "pbd2", "pbd1"))))
                .isTrue();
        assertThat(chain.isRunning()).isTrue();

        int tasksRun = drain();

        assertThat(events).containsExactly("start", "pbd3", "pbd2", "pbd1", "finish");
        assertThat(tasksRun).isEqualTo(5);
        assertThat(taskNames)
                .containsExactly(
                        "TestReconciler start",
                        "TestReconciler for `pbd3`",
                        "TestReconciler for `pbd2`",
                        "TestReconciler for `pbd1`",
                        "TestReconciler finish");
        assertThat(chain.isRunning()).isFalse();
    }

    @Test
    void finishesWhenThereAreNoGames() {
        chain.start(() -> new RecordingRun(List.of()));

        drain();

        assertThat(events).containsExactly("start", "finish");
        assertThat(chain.isRunning()).isFalse();
    }

    @Test
    void syncQueuedMidChainRunsBetweenGames() {
        // A save of pbd2 while pbd2 is being reconciled queues its sync behind the current task, so it must run
        // before the chain moves on to pbd1 rather than waiting for the whole reconciliation.
        onReconcile.put("pbd2", () -> queueTask("sync pbd2", 5, () -> events.add("sync pbd2")));
        chain.start(() -> new RecordingRun(List.of("pbd3", "pbd2", "pbd1")));

        drain();

        assertThat(events).containsExactly("start", "pbd3", "pbd2", "sync pbd2", "pbd1", "finish");
    }

    @Test
    void syncQueuedBeforeStartRunsBeforeTheFirstGame() {
        chain.start(() -> new RecordingRun(List.of("pbd2", "pbd1")));
        queueTask("sync pbd9", 5, () -> events.add("sync pbd9"));

        drain();

        assertThat(events).containsExactly("start", "sync pbd9", "pbd2", "pbd1", "finish");
    }

    @Test
    void maintenanceModeStopsTheChainAndAllowsALaterRun() {
        onReconcile.put("pbd2", () -> maintenanceMode = true);
        chain.start(() -> new RecordingRun(List.of("pbd3", "pbd2", "pbd1")));

        drain();

        assertThat(events).containsExactly("start", "pbd3", "pbd2");
        assertThat(chain.isRunning()).isFalse();

        maintenanceMode = false;
        events.clear();
        onReconcile.clear();
        assertThat(chain.start(() -> new RecordingRun(List.of("pbd1")))).isTrue();
        drain();
        assertThat(events).containsExactly("start", "pbd1", "finish");
    }

    @Test
    void maintenanceModeBeforeTheStartTaskRunsSkipsTheWholeRun() {
        chain.start(() -> new RecordingRun(List.of("pbd1")));
        maintenanceMode = true;

        drain();

        assertThat(events).isEmpty();
        assertThat(chain.isRunning()).isFalse();
    }

    @Test
    void secondStartWhileRunningIsIgnored() {
        List<Boolean> midChainStarts = new ArrayList<>();
        onReconcile.put("pbd1", () -> midChainStarts.add(chain.start(() -> new RecordingRun(List.of("other")))));

        assertThat(chain.start(() -> new RecordingRun(List.of("pbd2", "pbd1")))).isTrue();
        assertThat(chain.start(() -> new RecordingRun(List.of("other")))).isFalse();
        drain();

        assertThat(midChainStarts).containsExactly(false);
        assertThat(events).containsExactly("start", "pbd2", "pbd1", "finish");

        // Once the chain has finished, a new run is accepted again.
        assertThat(chain.start(() -> new RecordingRun(List.of("pbd1")))).isTrue();
    }

    @Test
    void startIsIgnoredWhileTheFinishTaskIsStillQueued() {
        chain.start(() -> new RecordingRun(List.of("pbd1")));
        executor.poll().run();
        executor.poll().run();

        assertThat(taskNames).last().isEqualTo("TestReconciler finish");
        assertThat(chain.start(() -> new RecordingRun(List.of("other")))).isFalse();

        drain();
        assertThat(events).containsExactly("start", "pbd1", "finish");
    }

    @Test
    void rejectedNextTaskReleasesTheChain() {
        onReconcile.put("pbd2", () -> shuttingDown = true);
        chain.start(() -> new RecordingRun(List.of("pbd3", "pbd2", "pbd1")));

        drain();

        assertThat(events).containsExactly("start", "pbd3", "pbd2");
        assertThat(chain.isRunning()).isFalse();
    }

    @Test
    void rejectedStartTaskReleasesTheChain() {
        shuttingDown = true;

        assertThat(chain.start(() -> new RecordingRun(List.of("pbd1")))).isFalse();

        assertThat(chain.isRunning()).isFalse();
        assertThat(executor).isEmpty();
    }

    @Test
    void failingGameStopsTheChainAndReleasesIt() {
        onReconcile.put("pbd2", () -> {
            throw new IllegalStateException("boom");
        });
        chain.start(() -> new RecordingRun(List.of("pbd3", "pbd2", "pbd1")));

        drain();

        assertThat(events).containsExactly("start", "pbd3", "pbd2");
        assertThat(chain.isRunning()).isFalse();
    }

    @Test
    void interruptedTaskStopsTheChain() {
        // Mirrors shutdownNow() interrupting the sync thread mid-chain.
        onReconcile.put("pbd2", () -> Thread.currentThread().interrupt());
        chain.start(() -> new RecordingRun(List.of("pbd3", "pbd2", "pbd1")));

        drain();

        assertThat(events).containsExactly("start", "pbd3", "pbd2");
        assertThat(chain.isRunning()).isFalse();
    }

    private boolean queueTask(String taskName, int warningThresholdSeconds, Runnable task) {
        if (shuttingDown) return false;
        taskNames.add(taskName);
        executor.add(task);
        return true;
    }

    private int drain() {
        int tasksRun = 0;
        while (!executor.isEmpty()) {
            executor.poll().run();
            tasksRun++;
        }
        return tasksRun;
    }

    private final class RecordingRun implements GameDatabaseReconciliationChain.Run {

        private final List<String> gameNames;

        private RecordingRun(List<String> gameNames) {
            this.gameNames = gameNames;
        }

        @Override
        public List<String> start() {
            events.add("start");
            return gameNames;
        }

        @Override
        public void reconcile(String gameName) {
            events.add(gameName);
            onReconcile.getOrDefault(gameName, () -> {}).run();
        }

        @Override
        public void finish() {
            events.add("finish");
        }
    }
}
