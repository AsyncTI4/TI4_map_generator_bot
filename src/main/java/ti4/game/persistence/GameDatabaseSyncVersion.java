package ti4.game.persistence;

import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GameDatabaseSyncVersion {

    private static final long MICROSECONDS_PER_MILLISECOND = 1000;
    private static final AtomicLong latestVersion = new AtomicLong();

    public static long next() {
        long currentMicroseconds = System.currentTimeMillis() * MICROSECONDS_PER_MILLISECOND;
        return latestVersion.updateAndGet(previous -> Math.max(previous + 1, currentMicroseconds));
    }
}
