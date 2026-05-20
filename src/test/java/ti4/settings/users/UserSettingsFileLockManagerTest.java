package ti4.settings.users;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class UserSettingsFileLockManagerTest {

    @Test
    void writeLockBlocksReadsForSameUser() throws Exception {
        assertTrue(runBlockingScenario(
                releaseLatch ->
                        () -> UserSettingsFileLockManager.wrapWithWriteLock("user-1", () -> awaitLatch(releaseLatch)),
                () -> UserSettingsFileLockManager.wrapWithReadLock("user-1", () -> {
                })));
    }

    @Test
    void readLockBlocksWritesForSameUser() throws Exception {
        assertTrue(runBlockingScenario(
                releaseLatch ->
                        () -> UserSettingsFileLockManager.wrapWithReadLock("user-1", () -> awaitLatch(releaseLatch)),
                () -> UserSettingsFileLockManager.wrapWithWriteLock("user-1", () -> {
                })));
    }

    private static boolean runBlockingScenario(LockingTaskFactory firstTaskFactory, Runnable blockedTask) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);
        try {
            Runnable firstTask = firstTaskFactory.create(releaseFirstTask);
            Future<?> firstFuture = executor.submit(() -> {
                firstTaskStarted.countDown();
                firstTask.run();
            });
            assertTrue(firstTaskStarted.await(1, TimeUnit.SECONDS));

            Future<Boolean> blockedFuture = executor.submit(() -> {
                blockedTask.run();
                return true;
            });

            assertThrows(
                    TimeoutException.class,
                    () -> blockedFuture.get(200, TimeUnit.MILLISECONDS),
                    "Second task should wait for the first lock to be released");

            releaseFirstTask.countDown();
            firstFuture.get(1, TimeUnit.SECONDS);
            return blockedFuture.get(1, TimeUnit.SECONDS);
        } finally {
            releaseFirstTask.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for latch", e);
        }
    }

    @FunctionalInterface
    private interface LockingTaskFactory {
        Runnable create(CountDownLatch releaseLatch);
    }
}
