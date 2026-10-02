package ti4.settings.users;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;

@UtilityClass
final class UserSettingsFileLockManager {

    private static final int LOCK_EXPIRE_AFTER_ACCESS_TIME_MINUTES = 20;
    private static final Cache<String, ReentrantReadWriteLock> locks = Caffeine.newBuilder()
            .expireAfterAccess(LOCK_EXPIRE_AFTER_ACCESS_TIME_MINUTES, TimeUnit.MINUTES)
            .build();

    private static ReentrantReadWriteLock getLock(String userId) {
        return locks.get(userId, _ -> new ReentrantReadWriteLock());
    }

    public static void wrapWithWriteLock(String userId, Runnable runnable) {
        ReentrantReadWriteLock lock = getLock(userId);
        lock.writeLock().lock();
        try {
            runnable.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static <T> T wrapWithWriteLock(String userId, Supplier<T> supplier) {
        ReentrantReadWriteLock lock = getLock(userId);
        lock.writeLock().lock();
        try {
            return supplier.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void wrapWithReadLock(String userId, Runnable runnable) {
        ReentrantReadWriteLock lock = getLock(userId);
        lock.readLock().lock();
        try {
            runnable.run();
        } finally {
            lock.readLock().unlock();
        }
    }

    public static <T> T wrapWithReadLock(String userId, Supplier<T> supplier) {
        ReentrantReadWriteLock lock = getLock(userId);
        lock.readLock().lock();
        try {
            return supplier.get();
        } finally {
            lock.readLock().unlock();
        }
    }
}
