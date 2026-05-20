package ti4.settings.users;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;

@UtilityClass
final class UserSettingsFileLockManager {

    private static final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    private static ReentrantReadWriteLock getLock(String userId) {
        return locks.computeIfAbsent(userId, _ -> new ReentrantReadWriteLock());
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
