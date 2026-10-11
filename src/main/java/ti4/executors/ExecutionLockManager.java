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
    private static final KeyedReadWriteLocks locks;

    static {
        Cache<String, ReentrantReadWriteLock> cache = Caffeine.newBuilder()
                .expireAfterAccess(LOCK_EXPIRE_AFTER_ACCESS_TIME_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
        CacheManager.registerCache("executionLocks", cache);
        locks = KeyedReadWriteLocks.backedBy(cache);
    }

    public static void lock(String lockName, ExecutionLockType lockType) {
        long lockStartTime = System.currentTimeMillis();
        locks.get(lockName, lockType).lock();
        long timeToGetLock = System.currentTimeMillis() - lockStartTime;
        if (timeToGetLock >= 1000) {
            BotLogger.warning("Took " + timeToGetLock + " ms to get lock \"" + lockName + "\"");
        }
    }

    public static void unlock(String lockName, ExecutionLockType lockType) {
        locks.get(lockName, lockType).unlock();
    }

    public static Runnable wrapWithTryLockAndRelease(String lockName, ExecutionLockType lockType, Runnable task) {
        return wrapWithTryLockAndRelease(lockName, lockType, task, null);
    }

    public static Runnable wrapWithTryLockAndRelease(
            String lockName, ExecutionLockType lockType, Runnable task, MessageChannel messageChannel) {
        if (isBlank(lockName)) throw new IllegalArgumentException("Lock name cannot be blank.");
        return () -> {
            if (locks.get(lockName, lockType).tryLock()) {
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
