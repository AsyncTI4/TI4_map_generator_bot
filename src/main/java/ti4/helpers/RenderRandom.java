package ti4.helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RenderRandom {

    private enum Mode {
        PRODUCTION,
        NEUTRAL,
        EAGER
    }

    private static volatile Mode mode = Mode.PRODUCTION;
    private static final Map<String, LongAdder> CALL_COUNTS = new ConcurrentHashMap<>();

    public static boolean oneIn(String tag, int x) {
        return switch (record(tag)) {
            case NEUTRAL -> false;
            case EAGER -> true;
            case PRODUCTION -> ThreadLocalRandom.current().nextInt(x) == 0;
        };
    }

    public static int nextInt(String tag, int bound) {
        return switch (record(tag)) {
            case NEUTRAL -> bound - 1;
            case EAGER -> 0;
            case PRODUCTION -> ThreadLocalRandom.current().nextInt(bound);
        };
    }

    public static int nextInt(String tag, int origin, int bound) {
        return switch (record(tag)) {
            case NEUTRAL -> bound - 1;
            case EAGER -> origin;
            case PRODUCTION -> ThreadLocalRandom.current().nextInt(origin, bound);
        };
    }

    public static int cosmeticInt(int bound) {
        return switch (mode) {
            case NEUTRAL -> bound - 1;
            case EAGER -> 0;
            case PRODUCTION -> ThreadLocalRandom.current().nextInt(bound);
        };
    }

    public static double nextDouble(String tag) {
        return switch (record(tag)) {
            case NEUTRAL -> 0.5;
            case EAGER -> 0;
            case PRODUCTION -> ThreadLocalRandom.current().nextDouble();
        };
    }

    public static <T> void shuffle(String tag, List<T> list) {
        if (record(tag) == Mode.PRODUCTION) {
            Collections.shuffle(list, ThreadLocalRandom.current());
        }
    }

    public static AutoCloseable neutral() {
        return enter(Mode.NEUTRAL);
    }

    public static AutoCloseable eager() {
        return enter(Mode.EAGER);
    }

    public static Map<String, Integer> callCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        CALL_COUNTS.forEach((tag, count) -> counts.put(tag, count.intValue()));
        return counts;
    }

    private static Mode record(String tag) {
        Mode current = mode;
        if (current != Mode.PRODUCTION) {
            CALL_COUNTS.computeIfAbsent(tag, ignored -> new LongAdder()).increment();
        }
        return current;
    }

    private static AutoCloseable enter(Mode newMode) {
        CALL_COUNTS.clear();
        mode = newMode;
        return () -> mode = Mode.PRODUCTION;
    }
}
