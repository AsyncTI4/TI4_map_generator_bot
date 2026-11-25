package ti4.ai.trace;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.ImaginedTurn;
import ti4.game.Game;

@UtilityClass
public class ShareCensus {

    public static final String PROPERTY = "ai.sharecensus";
    static final int PURE_KEYS_KEPT = 300_000;
    private static final String NO_SAVE = "";
    private static final String NO_SEAT = "(no seat)";
    private static final String IMAGINED = "/imagined";
    private static final ThreadLocal<long[]> TICK = ThreadLocal.withInitial(() -> new long[1]);
    private static final ThreadLocal<String> SAVE = ThreadLocal.withInitial(() -> NO_SAVE);
    private static final Map<String, SaveScope> SAVES = new ConcurrentHashMap<>();
    private static final Map<String, Map<Object, Seen>> PURE = new ConcurrentHashMap<>();
    private static final Map<String, Totals> TOTALS = new TreeMap<>();
    private static boolean enabled = Boolean.getBoolean(PROPERTY);

    public enum Repeat {
        SAME_SEAT_SAME_TICK("same seat, same tick"),
        SAME_SEAT_LATER_TICK("same seat, later tick"),
        OTHER_SEAT("another seat"),
        OTHER_SAVE("another save");

        private final String label;

        Repeat(String label) {
            this.label = label;
        }
    }

    private record SaveScope(String save, Map<String, Map<Object, Seen>> byName) {}

    private static final class Seen {
        private String save;
        private final Map<String, Long> ticksBySeat = new HashMap<>();

        private Seen(String save) {
            this.save = save;
        }

        private Repeat noteAndClassify(String seat, long tick, String currentSave) {
            Repeat repeat = classify(seat, tick, currentSave);
            if (repeat == Repeat.OTHER_SAVE) {
                save = currentSave;
                ticksBySeat.clear();
            }
            ticksBySeat.put(seat, tick);
            return repeat;
        }

        private Repeat classify(String seat, long tick, String currentSave) {
            if (ticksBySeat.isEmpty()) return null;
            if (!save.equals(currentSave)) return Repeat.OTHER_SAVE;
            Long last = ticksBySeat.get(seat);
            if (last == null) return Repeat.OTHER_SEAT;
            return last == tick ? Repeat.SAME_SEAT_SAME_TICK : Repeat.SAME_SEAT_LATER_TICK;
        }
    }

    private static final class Totals {
        private long computations;
        private long nanos;
        private final Map<Repeat, long[]> repeats = new EnumMap<>(Repeat.class);

        private void add(Repeat repeat, long elapsed) {
            computations++;
            nanos += elapsed;
            if (repeat == null) return;
            long[] counts = repeats.computeIfAbsent(repeat, ignored -> new long[2]);
            counts[0]++;
            counts[1] += elapsed;
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    static void enable(boolean on) {
        enabled = on;
    }

    public static void startTick(Game game) {
        if (!enabled) return;
        TICK.get()[0]++;
        SAVE.set(saveOf(game));
    }

    public static <T> T perSave(String name, Game game, Object key, Supplier<T> compute) {
        if (!enabled) return compute.get();
        String save = saveOf(game);
        SaveScope scope = SAVES.compute(
                game.getName(),
                (gameName, old) -> old != null && old.save().equals(save) ? old : new SaveScope(save, new HashMap<>()));
        Map<Object, Seen> seen = scope.byName().computeIfAbsent(name, ignored -> new HashMap<>());
        return measured(name, seen, key, save, compute);
    }

    public static <T> T pure(String name, Object key, Supplier<T> compute) {
        if (!enabled) return compute.get();
        Map<Object, Seen> seen = PURE.computeIfAbsent(name, ignored -> new ConcurrentHashMap<>());
        if (seen.size() > PURE_KEYS_KEPT) seen.clear();
        return measured(name, seen, key, SAVE.get(), compute);
    }

    private static <T> T measured(
            String name, Map<Object, Seen> seenByKey, Object key, String save, Supplier<T> compute) {
        Seen seen = seenByKey.computeIfAbsent(key, ignored -> new Seen(save));
        Repeat repeat;
        synchronized (seen) {
            repeat = seen.noteAndClassify(seat(), TICK.get()[0], save);
        }
        long start = System.nanoTime();
        try {
            return compute.get();
        } finally {
            long elapsed = System.nanoTime() - start;
            synchronized (TOTALS) {
                TOTALS.computeIfAbsent(name, ignored -> new Totals()).add(repeat, elapsed);
            }
        }
    }

    private static String saveOf(Game game) {
        return game.getName() + "@" + game.getLastModifiedDate();
    }

    private static String seat() {
        String deciding = DecidingSeat.currentUserId();
        String seat = deciding == null ? NO_SEAT : deciding;
        return ImaginedTurn.underway() ? seat + IMAGINED : seat;
    }

    public static List<String> lines() {
        synchronized (TOTALS) {
            List<String> lines = new ArrayList<>();
            lines.add("SHARECENSUS computations that ran again with the same key; times include nested census calls");
            TOTALS.forEach((name, totals) -> lines.add(line(name, totals)));
            return lines;
        }
    }

    private static String line(String name, Totals totals) {
        long repeated = 0;
        long repeatedNanos = 0;
        StringBuilder parts = new StringBuilder();
        for (Repeat repeat : Repeat.values()) {
            long[] counts = totals.repeats.get(repeat);
            if (counts == null) continue;
            repeated += counts[0];
            repeatedNanos += counts[1];
            parts.append(String.format(Locale.ROOT, "; %s %d (%.1f s)", repeat.label, counts[0], seconds(counts[1])));
        }
        return String.format(
                Locale.ROOT,
                "SHARECENSUS %s: %d computations, %.1f s; repeats %d (%s of calls) taking %.1f s (%s of its time)%s",
                name,
                totals.computations,
                seconds(totals.nanos),
                repeated,
                percent(repeated, totals.computations),
                seconds(repeatedNanos),
                percent(repeatedNanos, totals.nanos),
                parts);
    }

    private static double seconds(long nanos) {
        return nanos / 1e9;
    }

    private static String percent(long part, long whole) {
        return whole == 0 ? "0%" : String.format(Locale.ROOT, "%.0f%%", 100.0 * part / whole);
    }

    public static void reset() {
        synchronized (TOTALS) {
            TOTALS.clear();
        }
        SAVES.clear();
        PURE.clear();
        TICK.get()[0] = 0;
        SAVE.remove();
    }
}
