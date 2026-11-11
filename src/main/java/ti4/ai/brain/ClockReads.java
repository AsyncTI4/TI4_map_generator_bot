package ti4.ai.brain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ClockReads {

    private static final ThreadLocal<Boolean> READ = ThreadLocal.withInitial(() -> false);

    public static void reset() {
        READ.set(false);
    }

    public static boolean read() {
        return READ.get();
    }

    static void note() {
        READ.set(true);
    }
}
