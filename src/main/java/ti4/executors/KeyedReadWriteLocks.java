package ti4.executors;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

public final class KeyedReadWriteLocks {

    private final Function<String, ReentrantReadWriteLock> lockLookup;

    private KeyedReadWriteLocks(Function<String, ReentrantReadWriteLock> lockLookup) {
        this.lockLookup = lockLookup;
    }

    public static KeyedReadWriteLocks unbounded() {
        ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
        return new KeyedReadWriteLocks(key -> locks.computeIfAbsent(key, _ -> new ReentrantReadWriteLock()));
    }

    public static KeyedReadWriteLocks backedBy(Cache<String, ReentrantReadWriteLock> cache) {
        return new KeyedReadWriteLocks(key -> cache.get(key, _ -> new ReentrantReadWriteLock()));
    }

    public Lock get(String key, ExecutionLockType lockType) {
        ReentrantReadWriteLock lock = lockLookup.apply(key);
        return lockType == ExecutionLockType.READ ? lock.readLock() : lock.writeLock();
    }

    public <T> T withLock(String key, ExecutionLockType lockType, Supplier<T> supplier) {
        Lock lock = get(key, lockType);
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public void withLock(String key, ExecutionLockType lockType, Runnable runnable) {
        withLock(key, lockType, () -> {
            runnable.run();
            return null;
        });
    }
}
