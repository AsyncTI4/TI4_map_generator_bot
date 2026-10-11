package ti4.game.persistence;

import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockType;
import ti4.executors.KeyedReadWriteLocks;

@UtilityClass
final class GameFileLockManager {

    private static final KeyedReadWriteLocks locks = KeyedReadWriteLocks.unbounded();

    public static <T> T wrapWithWriteLock(String gameName, Supplier<T> supplier) {
        return locks.withLock(gameName, ExecutionLockType.WRITE, supplier);
    }

    public static <T> T wrapWithReadLock(String gameName, Supplier<T> supplier) {
        return locks.withLock(gameName, ExecutionLockType.READ, supplier);
    }
}
