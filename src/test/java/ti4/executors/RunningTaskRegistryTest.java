package ti4.executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import ti4.helpers.TimedRunnable;

class RunningTaskRegistryTest {

    @Test
    void tracksConcurrentVirtualTasksWithTheSameNameIndependently() throws Exception {
        String name = "concurrent task";
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("registry-test-", 0).factory())) {
            Runnable runnable = RunningTaskRegistry.wrap(name, () -> {
                started.countDown();
                await(release);
            });
            var first = executor.submit(runnable);
            var second = executor.submit(runnable);
            try {
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                var snapshot = tasksNamed(name);
                assertThat(snapshot).hasSize(2);
                assertThat(snapshot)
                        .extracting(RunningTaskRegistry.RunningTask::id)
                        .doesNotHaveDuplicates();
                assertThat(snapshot).allSatisfy(task -> {
                    assertThat(task.threadName()).startsWith("registry-test-");
                    assertThat(task.elapsed().toNanos()).isNotNegative();
                    assertThat(task.startedAt()).isNotNull();
                    assertThat(task.threadState()).isNotNull();
                });
            } finally {
                release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(tasksNamed(name)).isEmpty();
        }
    }

    @Test
    void queuedTasksAreOnlyReportedWhenTheyStart() throws Exception {
        String name = "queued task";
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> await(release));
            var queued = executor.submit(RunningTaskRegistry.wrap(
                    name, () -> assertThat(tasksNamed(name)).hasSize(1)));
            try {
                assertThat(tasksNamed(name)).isEmpty();
            } finally {
                release.countDown();
            }
            queued.get(10, TimeUnit.SECONDS);
            assertThat(tasksNamed(name)).isEmpty();
        }
    }

    @Test
    void timedRunnableIsRemovedWhenItsDelegateThrows() {
        String name = "failing timed task";
        TimedRunnable runnable = new TimedRunnable(name, () -> {
            assertThat(tasksNamed(name)).hasSize(1);
            throw new IllegalStateException("test failure");
        });
        assertThrows(IllegalStateException.class, runnable::run);
        assertThat(tasksNamed(name)).isEmpty();
    }

    @Test
    void wrappedScheduledJobIsRemovedAfterAnError() {
        String name = "failing scheduled task";
        Runnable runnable = RunningTaskRegistry.wrap(name, () -> {
            assertThat(tasksNamed(name)).hasSize(1);
            throw new AssertionError("test error");
        });
        assertThrows(AssertionError.class, runnable::run);
        assertThat(tasksNamed(name)).isEmpty();
    }

    private static List<RunningTaskRegistry.RunningTask> tasksNamed(String name) {
        return RunningTaskRegistry.snapshot().stream()
                .filter(task -> task.name().equals(name))
                .toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for release");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
