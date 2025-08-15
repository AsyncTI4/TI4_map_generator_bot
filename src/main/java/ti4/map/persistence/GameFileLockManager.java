package ti4.map.persistence;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

class GameFileLockManager {

    private static final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    private static ReentrantReadWriteLock getLock(String gameName) {
        return locks.computeIfAbsent(gameName, k -> new ReentrantReadWriteLock());
    }

    public static void wrapWithWriteLock(String gameName, Runnable runnable) {
        ReentrantReadWriteLock lock = GameFileLockManager.getLock(gameName);
        lock.writeLock().lock();
        try {
            runnable.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static <T> T wrapWithWriteLock(String gameName, Supplier<T> supplier) {
        ReentrantReadWriteLock lock = GameFileLockManager.getLock(gameName);
        lock.writeLock().lock();
        try {
            return supplier.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void wrapWithReadLock(String gameName, Runnable runnable) {
        ReentrantReadWriteLock lock = getLock(gameName);
        lock.readLock().lock();
        try {
            runnable.run();
        } finally {
            lock.readLock().unlock();
        }
    }

    public static <T> T wrapWithReadLock(String gameName, Supplier<T> supplier) {
        ReentrantReadWriteLock lock = getLock(gameName);
        lock.readLock().lock();
        try {
            return supplier.get();
        } finally {
            lock.readLock().unlock();
        }
    }
}
