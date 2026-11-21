package ti4.ai.trace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ShadowCheck {

    public static final String PROPERTY = "ai.shadow";
    private static final ThreadLocal<Set<String>> TRIGGERED = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Set<String>> CHANGED = ThreadLocal.withInitial(HashSet::new);
    private static final Map<String, long[]> COUNTS = new TreeMap<>();
    private static boolean enabled = Boolean.getBoolean(PROPERTY);

    private static final int CALLS = 0;
    private static final int CALLS_CHANGED = 1;
    private static final int DECISIONS = 2;
    private static final int DECISIONS_CHANGED = 3;
    private static final String ANY = "(any check)";

    public static boolean enabled() {
        return enabled;
    }

    static void enable(boolean on) {
        enabled = on;
    }

    public static boolean choose(String name, boolean current, BooleanSupplier candidate) {
        if (!enabled) return candidate.getAsBoolean();
        boolean changed = candidate.getAsBoolean() != current;
        TRIGGERED.get().add(name);
        if (changed) CHANGED.get().add(name);
        synchronized (COUNTS) {
            long[] counts = COUNTS.computeIfAbsent(name, key -> new long[4]);
            counts[CALLS]++;
            if (changed) counts[CALLS_CHANGED]++;
        }
        return current;
    }

    public static void startDecision() {
        TRIGGERED.get().clear();
        CHANGED.get().clear();
    }

    public static void endDecision() {
        if (!enabled || TRIGGERED.get().isEmpty()) return;
        synchronized (COUNTS) {
            for (String name : TRIGGERED.get()) {
                long[] counts = COUNTS.computeIfAbsent(name, key -> new long[4]);
                counts[DECISIONS]++;
                if (CHANGED.get().contains(name)) counts[DECISIONS_CHANGED]++;
            }
            long[] any = COUNTS.computeIfAbsent(ANY, key -> new long[4]);
            any[DECISIONS]++;
            if (!CHANGED.get().isEmpty()) any[DECISIONS_CHANGED]++;
        }
        startDecision();
    }

    public static List<String> lines() {
        synchronized (COUNTS) {
            List<String> lines = new ArrayList<>();
            if (COUNTS.isEmpty()) {
                lines.add("SHADOW no checks recorded (run with -D" + PROPERTY + "=true)");
                return lines;
            }
            lines.add(
                    "SHADOW candidate answer vs the one played (decisions triggered, changed, share; calls, changed):");
            COUNTS.forEach((name, counts) -> lines.add(String.format(
                    Locale.ROOT,
                    "SHADOW   %s: %d decisions, %d changed (%.1f%%); %d calls, %d changed",
                    name,
                    counts[DECISIONS],
                    counts[DECISIONS_CHANGED],
                    counts[DECISIONS] == 0 ? 0 : 100.0 * counts[DECISIONS_CHANGED] / counts[DECISIONS],
                    counts[CALLS],
                    counts[CALLS_CHANGED])));
            return lines;
        }
    }

    public static void reset() {
        synchronized (COUNTS) {
            COUNTS.clear();
        }
        startDecision();
    }
}
