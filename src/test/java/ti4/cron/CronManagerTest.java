package ti4.cron;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ti4.executors.RunningTaskRegistry;

class CronManagerTest {

    @Test
    void reportsRunningCronsAndRemovesThemAfterCompletion() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        CronManager.scheduleOnce(
                TrackedCron.class,
                () -> {
                    started.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS))
                            throw new AssertionError("Timed out waiting for release");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                },
                0,
                TimeUnit.MILLISECONDS);
        try {
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(RunningTaskRegistry.snapshot()).anyMatch(task -> "Cron: TrackedCron".equals(task.name()));
        } finally {
            release.countDown();
        }
        // A later task on the single cron thread guarantees the first task has exited its tracking scope.
        CronManager.scheduleOnce(AfterTrackedCron.class, completed::countDown, 0, TimeUnit.MILLISECONDS);
        assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(RunningTaskRegistry.snapshot()).noneMatch(task -> "Cron: TrackedCron".equals(task.name()));
    }

    @Test
    void periodicCronStillRunsOnItsNextTickAfterARunThrows() throws InterruptedException {
        AtomicBoolean hasThrown = new AtomicBoolean();
        CountDownLatch ranAgainAfterThrowing = new CountDownLatch(1);
        Runnable throwsOnFirstRun = () -> {
            if (hasThrown.compareAndSet(false, true)) {
                // The failure AutoPingCron hits when GameManager's warmup latch times out.
                throw new IllegalStateException("Failed to wait for warmup.");
            }
            ranAgainAfterThrowing.countDown();
        };

        ScheduledFuture<?> schedule =
                CronManager.schedulePeriodically(ThrowingCron.class, throwsOnFirstRun, 0, 10, TimeUnit.MILLISECONDS);
        try {
            assertThat(ranAgainAfterThrowing.await(10, TimeUnit.SECONDS))
                    .as("cron ran again after its first run threw")
                    .isTrue();
            assertThat(schedule.isDone()).as("schedule is still active").isFalse();
        } finally {
            // CronManager's scheduler is static and shared by every test in this JVM, so stop the test cron.
            schedule.cancel(false);
        }
    }

    @Test
    void pendingDelayedTaskDoesNotHoldUpShutdown() throws InterruptedException {
        // CardsInfoPinCleanupCron registers a manual-only task delayed by Long.MAX_VALUE days. Before the
        // shutdown policy was set, that task kept the scheduler alive, so every deploy waited the full
        // graceful window (15 s) before the lease could be handed over.
        ScheduledThreadPoolExecutor scheduler = CronManager.newScheduler();
        scheduler.schedule(() -> {}, Long.MAX_VALUE, TimeUnit.DAYS);

        scheduler.shutdown();

        assertThat(scheduler.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shutdownStillWaitsForACronThatIsAlreadyRunning() throws InterruptedException {
        ScheduledThreadPoolExecutor scheduler = CronManager.newScheduler();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        scheduler.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            finished.set(true);
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        scheduler.shutdown();
        assertThat(scheduler.awaitTermination(200, TimeUnit.MILLISECONDS))
                .as("scheduler waits while a cron is mid-run")
                .isFalse();

        release.countDown();
        assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(finished).isTrue();
    }

    private static final class ThrowingCron {}

    private static final class TrackedCron {}

    private static final class AfterTrackedCron {}
}
