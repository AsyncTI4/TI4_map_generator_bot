package ti4.executors;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class ExecutionLockManager {

    private static final ConcurrentMap<String, Entry> locks = new ConcurrentHashMap<>();

    private static final class Entry {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private int holders;

        private Lock lockOf(ExecutionLockType lockType) {
            return lockType == ExecutionLockType.READ ? lock.readLock() : lock.writeLock();
        }
    }

    public static void lock(String lockName, ExecutionLockType lockType) {
        Entry entry = acquire(lockName);
        long lockStartTime = System.currentTimeMillis();
        try {
            entry.lockOf(lockType).lock();
        } catch (RuntimeException | Error e) {
            release(lockName);
            throw e;
        }
        long timeToGetLock = System.currentTimeMillis() - lockStartTime;
        if (timeToGetLock >= 1000) {
            BotLogger.warning("Took " + timeToGetLock + " ms to get lock \"" + lockName + "\"");
        }
    }

    private static boolean tryLock(String lockName, ExecutionLockType lockType) {
        Entry entry = acquire(lockName);
        boolean gotLock = false;
        try {
            gotLock = entry.lockOf(lockType).tryLock();
            return gotLock;
        } finally {
            if (!gotLock) {
                release(lockName);
            }
        }
    }

    public static void unlock(String lockName, ExecutionLockType lockType) {
        Entry entry = locks.get(lockName);
        if (entry == null) {
            throw new IllegalMonitorStateException("Lock \"" + lockName + "\" is not held.");
        }
        entry.lockOf(lockType).unlock();
        release(lockName);
    }

    static int trackedLockCount() {
        return locks.size();
    }

    private static Entry acquire(String lockName) {
        return locks.compute(lockName, (_, existing) -> {
            Entry entry = existing == null ? new Entry() : existing;
            entry.holders++;
            return entry;
        });
    }

    private static void release(String lockName) {
        locks.compute(lockName, (_, entry) -> {
            if (entry == null) {
                return null;
            }
            entry.holders--;
            return entry.holders > 0 ? entry : null;
        });
    }

    public static Runnable wrapWithTryLockAndRelease(String lockName, ExecutionLockType lockType, Runnable task) {
        return wrapWithTryLockAndRelease(lockName, lockType, task, null);
    }

    public static Runnable wrapWithTryLockAndRelease(
            String lockName, ExecutionLockType lockType, Runnable task, MessageChannel messageChannel) {
        if (isBlank(lockName)) throw new IllegalArgumentException("Lock name cannot be blank.");
        return () -> {
            boolean gotLock = tryLock(lockName, lockType);
            if (gotLock) {
                runAndUnlock(lockName, lockType, task);
                return;
            }
            if (messageChannel != null) {
                MessageHelper.sendMessageToChannel(
                        messageChannel,
                        "The bot hasn't finished processing the last task for " + lockName + ". Please wait.");
            } else {
                BotLogger.warning("The bot hasn't finished processing the last task for " + lockName + ".");
            }
        };
    }

    private static void runAndUnlock(String lockName, ExecutionLockType lockType, Runnable task) {
        try {
            task.run();
        } finally {
            unlock(lockName, lockType);
        }
    }

    public static Runnable wrapWithLockAndRelease(String lockName, ExecutionLockType lockType, Runnable task) {
        if (isBlank(lockName)) throw new IllegalArgumentException("Lock name cannot be blank.");
        return () -> {
            lock(lockName, lockType);
            runAndUnlock(lockName, lockType, task);
        };
    }
}
