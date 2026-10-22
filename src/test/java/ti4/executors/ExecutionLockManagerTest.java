package ti4.executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionLockManagerTest {

    private static final int TIMEOUT_SECONDS = 10;
    private static final int SETTLE_MILLIS = 100;

    private ExecutorService executor;
    private String lockName;

    @BeforeEach
    void setUp() {
        executor = Executors.newCachedThreadPool();
        lockName = "lock-" + System.nanoTime();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private void awaitQueuedThreads() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!ExecutionLockManager.hasQueuedThreads(lockName)) {
            assertTrue(System.nanoTime() < deadline, "no thread queued on the lock");
            Thread.sleep(5);
        }
        Thread.sleep(SETTLE_MILLIS);
    }

    private void awaitHolders(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (ExecutionLockManager.holders(lockName) != expected) {
            assertTrue(System.nanoTime() < deadline, "lock never reached " + expected + " holders");
            Thread.sleep(5);
        }
    }

    private Future<?> holdUntil(ExecutionLockType type, CountDownLatch holding, CountDownLatch release) {
        return executor.submit(ExecutionLockManager.wrapWithLockAndRelease(
                lockName, type, () -> awaitAfterSignalling(holding, release)));
    }

    @Test
    void twoWritersAreMutuallyExclusive() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        Runnable task = ExecutionLockManager.wrapWithLockAndRelease(lockName, ExecutionLockType.WRITE, () -> {
            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inside.decrementAndGet();
        });

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(executor.submit(task));
        }
        for (Future<?> future : futures) {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertEquals(1, maxInside.get());
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void readersShareTheLock() throws Exception {
        CountDownLatch bothInside = new CountDownLatch(2);
        Runnable reader = ExecutionLockManager.wrapWithLockAndRelease(lockName, ExecutionLockType.READ, () -> {
            bothInside.countDown();
            try {
                assertTrue(bothInside.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Future<?> first = executor.submit(reader);
        Future<?> second = executor.submit(reader);

        first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void aReaderAndAWriterExcludeEachOther() throws Exception {
        CountDownLatch readerHolding = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        AtomicBoolean writerRan = new AtomicBoolean();
        Future<?> reader = holdUntil(ExecutionLockType.READ, readerHolding, releaseReader);
        assertTrue(readerHolding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        Future<?> writer = executor.submit(ExecutionLockManager.wrapWithLockAndRelease(
                lockName, ExecutionLockType.WRITE, () -> writerRan.set(true)));
        awaitQueuedThreads();
        assertFalse(writerRan.get());

        releaseReader.countDown();
        reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        writer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(writerRan.get());
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void aWriterAndAReaderExcludeEachOther() throws Exception {
        CountDownLatch writerHolding = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        AtomicBoolean readerRan = new AtomicBoolean();
        Future<?> writer = holdUntil(ExecutionLockType.WRITE, writerHolding, releaseWriter);
        assertTrue(writerHolding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        Future<?> reader = executor.submit(ExecutionLockManager.wrapWithLockAndRelease(
                lockName, ExecutionLockType.READ, () -> readerRan.set(true)));
        awaitQueuedThreads();
        assertFalse(readerRan.get());

        releaseWriter.countDown();
        writer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(readerRan.get());
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void noLocksAreTrackedOnceEverythingIsReleased() throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ExecutionLockType type = i % 3 == 0 ? ExecutionLockType.WRITE : ExecutionLockType.READ;
            futures.add(
                    executor.submit(ExecutionLockManager.wrapWithLockAndRelease(lockName + (i % 4), type, () -> {})));
        }
        for (Future<?> future : futures) {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        for (int i = 0; i < 4; i++) {
            assertEquals(0, ExecutionLockManager.holders(lockName + i));
        }
    }

    @Test
    void aLockHeldWhileOthersWaitIsNeverReplaced() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        Future<?> holder =
                executor.submit(ExecutionLockManager.wrapWithLockAndRelease(lockName, ExecutionLockType.WRITE, () -> {
                    inside.incrementAndGet();
                    awaitAfterSignalling(holding, release);
                    inside.decrementAndGet();
                }));
        assertTrue(holding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        List<Future<?>> waiters = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            waiters.add(executor.submit(
                    ExecutionLockManager.wrapWithLockAndRelease(lockName, ExecutionLockType.WRITE, () -> {
                        maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                        inside.decrementAndGet();
                    })));
        }
        awaitHolders(4);
        awaitQueuedThreads();
        assertEquals(4, ExecutionLockManager.holders(lockName));
        assertEquals(1, inside.get());

        release.countDown();
        holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (Future<?> waiter : waiters) {
            waiter.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        assertEquals(1, maxInside.get());
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void aFailedTryLockLeavesNoCountBehind() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = holdUntil(ExecutionLockType.WRITE, holding, release);
        assertTrue(holding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        AtomicBoolean ran = new AtomicBoolean();

        for (int i = 0; i < 5; i++) {
            executor.submit(ExecutionLockManager.wrapWithTryLockAndRelease(
                            lockName, ExecutionLockType.WRITE, () -> ran.set(true)))
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        assertFalse(ran.get());
        assertEquals(1, ExecutionLockManager.holders(lockName));

        release.countDown();
        holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void aFailedWriteTryLockWhileTheSameThreadHoldsAReadKeepsItsCount() {
        ExecutionLockManager.lock(lockName, ExecutionLockType.READ);
        AtomicBoolean ran = new AtomicBoolean();

        ExecutionLockManager.wrapWithTryLockAndRelease(lockName, ExecutionLockType.WRITE, () -> ran.set(true))
                .run();

        assertFalse(ran.get());
        assertEquals(1, ExecutionLockManager.holders(lockName));
        ExecutionLockManager.unlock(lockName, ExecutionLockType.READ);
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void aSuccessfulTryLockRunsTheTaskAndReleases() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();

        executor.submit(ExecutionLockManager.wrapWithTryLockAndRelease(
                        lockName, ExecutionLockType.WRITE, () -> ran.set(true)))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTrue(ran.get());
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void aThrowingTaskStillReleasesTheLock() throws Exception {
        Runnable failing = ExecutionLockManager.wrapWithLockAndRelease(lockName, ExecutionLockType.WRITE, () -> {
            throw new IllegalStateException("boom");
        });

        assertThrows(IllegalStateException.class, failing::run);

        assertEquals(0, ExecutionLockManager.holders(lockName));
        executor.submit(ExecutionLockManager.wrapWithLockAndRelease(lockName, ExecutionLockType.WRITE, () -> {}))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void unlockingALockThatIsNotHeldThrowsAndTracksNothing() {
        assertThrows(
                IllegalMonitorStateException.class,
                () -> ExecutionLockManager.unlock(lockName, ExecutionLockType.WRITE));

        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void unlockingAnotherThreadsLockThrowsAndKeepsItsCount() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = holdUntil(ExecutionLockType.WRITE, holding, release);
        assertTrue(holding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertThrows(
                IllegalMonitorStateException.class,
                () -> ExecutionLockManager.unlock(lockName, ExecutionLockType.WRITE));
        assertThrows(
                IllegalMonitorStateException.class,
                () -> ExecutionLockManager.unlock(lockName, ExecutionLockType.READ));
        assertEquals(1, ExecutionLockManager.holders(lockName));

        release.countDown();
        holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void theSameThreadMayReacquireAndMustReleaseEachTime() {
        ExecutionLockManager.lock(lockName, ExecutionLockType.WRITE);
        ExecutionLockManager.lock(lockName, ExecutionLockType.WRITE);
        ExecutionLockManager.unlock(lockName, ExecutionLockType.WRITE);
        assertEquals(1, ExecutionLockManager.holders(lockName));
        ExecutionLockManager.unlock(lockName, ExecutionLockType.WRITE);

        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    @Test
    void aWriterMayTakeAReadLockAndReleaseBothInAnyOrder() {
        ExecutionLockManager.lock(lockName, ExecutionLockType.WRITE);
        ExecutionLockManager.lock(lockName, ExecutionLockType.READ);
        assertEquals(2, ExecutionLockManager.holders(lockName));

        ExecutionLockManager.unlock(lockName, ExecutionLockType.WRITE);
        assertEquals(1, ExecutionLockManager.holders(lockName));
        ExecutionLockManager.unlock(lockName, ExecutionLockType.READ);

        assertEquals(0, ExecutionLockManager.holders(lockName));
    }

    private static void awaitAfterSignalling(CountDownLatch signal, CountDownLatch until) {
        signal.countDown();
        try {
            until.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
