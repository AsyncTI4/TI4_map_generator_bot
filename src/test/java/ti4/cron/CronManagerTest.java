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

    private static final class ThrowingCron {}
}
