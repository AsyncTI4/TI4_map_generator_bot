package ti4.executors;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class ExecutionLockManager {

    private static final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public static void lock(String lockName, LockType lockType) {
        var lock = getLock(lockName);
        if (lockType == LockType.READ) {
            lock.readLock().lock();
        } else {
            lock.writeLock().lock();
        }
    }

    public static boolean tryLock(String lockName, LockType lockType) {
        var lock = getLock(lockName);
        if (lockType == LockType.READ) {
            return lock.readLock().tryLock();
        }
        return lock.writeLock().tryLock();
    }

    public static void unlock(String lockName, LockType lockType) {
        var lock = getLock(lockName);
        if (lockType == LockType.READ) {
            lock.readLock().unlock();
        } else {
            lock.writeLock().unlock();
        }
    }

    private static ReentrantReadWriteLock getLock(String lockName) {
        return locks.computeIfAbsent(lockName, k -> new ReentrantReadWriteLock());
    }

    public static Runnable wrapWithTryLockAndRelease(
            String lockName, LockType lockType, Runnable task, MessageChannel messageChannel) {
        return () -> {
            boolean gotLock = ExecutionLockManager.tryLock(lockName, ExecutionLockManager.LockType.WRITE);
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

    private static void runAndUnlock(String lockName, LockType lockType, Runnable task) {
        try {
            task.run();
        } finally {
            unlock(lockName, lockType);
        }
    }

    public static Runnable wrapWithTryLockAndRelease(String lockName, LockType lockType, Runnable task) {
        return wrapWithTryLockAndRelease(lockName, lockType, task, null);
    }

    public static Runnable wrapWithLockAndRelease(String lockName, LockType lockType, Runnable task) {
        return () -> {
            ExecutionLockManager.lock(lockName, ExecutionLockManager.LockType.WRITE);
            runAndUnlock(lockName, lockType, task);
        };
    }

    public enum LockType {
        READ,
        WRITE
    }
}
