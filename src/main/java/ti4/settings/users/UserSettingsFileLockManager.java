package ti4.settings.users;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockType;
import ti4.executors.KeyedReadWriteLocks;

@UtilityClass
final class UserSettingsFileLockManager {

    private static final int LOCK_EXPIRE_AFTER_ACCESS_TIME_MINUTES = 20;
    private static final KeyedReadWriteLocks locks = KeyedReadWriteLocks.backedBy(Caffeine.newBuilder()
            .expireAfterAccess(LOCK_EXPIRE_AFTER_ACCESS_TIME_MINUTES, TimeUnit.MINUTES)
            .build());

    public static void wrapWithWriteLock(String userId, Runnable runnable) {
        locks.withLock(userId, ExecutionLockType.WRITE, runnable);
    }

    public static <T> T wrapWithReadLock(String userId, Supplier<T> supplier) {
        return locks.withLock(userId, ExecutionLockType.READ, supplier);
    }
}
