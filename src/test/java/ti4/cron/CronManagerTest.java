package ti4.cron;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CronManagerTest {

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
    void oneOffTasksAreNotOfferedToRunCron() {
        CronManager.scheduleOnce(OneOffTask.class, () -> {}, 0, TimeUnit.MILLISECONDS);

        assertThat(CronManager.getCronNames()).doesNotContain(OneOffTask.class.getSimpleName());
        assertThat(CronManager.runCron(OneOffTask.class.getSimpleName())).isFalse();
    }

    @Test
    void manualOnlyJobsRunOnlyWhenAskedFor() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        CronManager.registerManualOnly(ManualOnlyCron.class, ran::countDown);

        // Nothing is scheduled, so the job must not run on its own.
        assertThat(ran.await(200, TimeUnit.MILLISECONDS)).isFalse();

        assertThat(CronManager.runCron(ManualOnlyCron.class.getSimpleName())).isTrue();
        assertThat(ran.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void leaseStartJobsWaitForTheLeaseAndRunAgainOnEachAcquisition() throws InterruptedException {
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch secondRun = new CountDownLatch(2);
        Runnable job = () -> {
            firstRun.countDown();
            secondRun.countDown();
        };

        // The only test in this JVM that calls runLeaseStartJobs, so the lease has not been acquired yet.
        CronManager.scheduleOnceAfterLeaseAcquired(LeaseStartCron.class, job, 0, TimeUnit.MILLISECONDS);
        assertThat(firstRun.await(200, TimeUnit.MILLISECONDS))
                .as("job waits for the lease")
                .isFalse();
        assertThat(CronManager.getCronNames()).contains(LeaseStartCron.class.getSimpleName());

        CronManager.runLeaseStartJobs();
        assertThat(firstRun.await(10, TimeUnit.SECONDS))
                .as("job runs once the lease is acquired")
                .isTrue();

        // Losing the lease and acquiring it again later reruns the startup jobs.
        CronManager.runLeaseStartJobs();
        assertThat(secondRun.await(10, TimeUnit.SECONDS))
                .as("job runs on reacquisition")
                .isTrue();
    }

    private static final class ThrowingCron {}

    private static final class OneOffTask {}

    private static final class ManualOnlyCron {}

    private static final class LeaseStartCron {}
}
