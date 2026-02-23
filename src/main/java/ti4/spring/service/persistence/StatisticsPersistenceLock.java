package ti4.spring.service.persistence;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StatisticsPersistenceLock {

    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock(true);

    public static void runWithReadLock(Runnable runnable) {
        var readLock = LOCK.readLock();
        readLock.lock();
        try {
            runnable.run();
        } finally {
            readLock.unlock();
        }
    }

    public static void runWithWriteLock(Runnable runnable) {
        var writeLock = LOCK.writeLock();
        writeLock.lock();
        try {
            runnable.run();
        } finally {
            writeLock.unlock();
        }
    }
}
