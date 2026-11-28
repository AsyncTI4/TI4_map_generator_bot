package ti4.helpers;

import java.util.function.LongSupplier;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GameClock {

    private static final ThreadLocal<LongSupplier> CLOCK = new ThreadLocal<>();

    public static long currentTimeMillis() {
        LongSupplier clock = CLOCK.get();
        return clock != null ? clock.getAsLong() : System.currentTimeMillis();
    }

    public static void useForThisThread(LongSupplier clock) {
        CLOCK.set(clock);
    }

    public static void useWallClockForThisThread() {
        CLOCK.remove();
    }
}
