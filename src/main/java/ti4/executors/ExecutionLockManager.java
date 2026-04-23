package ti4.executors;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.cache.CacheManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class ExecutionLockManager {

    private static final int LOCK_EXPIRE_AFTER_ACCESS_TIME_MINUTES = 20;
    private static final Cache<String, ReentrantReadWriteLock> locks;

    static {
        locks = Caffeine.newBuilder()
                .expireAfterAccess(LOCK_EXPIRE_AFTER_ACCESS_TIME_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
        CacheManager.registerCache("executionLocks", locks);
    }

    public static void lock(String lockName, LockType lockType) {
        var lock = getLock(lockName);
        if (lockType == LockType.READ) {
            lock.readLock().lock();
        } else {
            lock.writeLock().lock();
        }
    }

    private static boolean tryLock(String lockName, LockType lockType) {
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
        return locks.get(lockName, k -> new ReentrantReadWriteLock());
    }

    public static Runnable wrapWithTryLockAndRelease(String lockName, LockType lockType, Runnable task) {
        return wrapWithTryLockAndRelease(lockName, lockType, task, null);
    }

    public static Runnable wrapWithTryLockAndRelease(
            String lockName, LockType lockType, Runnable task, MessageChannel messageChannel) {
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

    private static void runAndUnlock(String lockName, LockType lockType, Runnable task) {
        try {
            task.run();
        } finally {
            unlock(lockName, lockType);
        }
    }

    public static Runnable wrapWithLockAndRelease(String lockName, LockType lockType, Runnable task) {
        if (isBlank(lockName)) throw new IllegalArgumentException("Lock name cannot be blank.");
        return () -> {
            lock(lockName, lockType);
            runAndUnlock(lockName, lockType, task);
        };
    }

    public enum LockType {
        READ,
        WRITE
    }
}
