package ti4.ai.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class TickMemo {

    public static final String VERIFY_PROPERTY = "ai.tickmemo.verify";
    private static final int MISMATCHES_KEPT = 5;
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final Map<String, long[]> CHECKS = new TreeMap<>();
    private static final List<String> MISMATCHES = new ArrayList<>();
    private static boolean verifying = Boolean.getBoolean(VERIFY_PROPERTY);

    public interface Open extends AutoCloseable {
        @Override
        void close();
    }

    public static Open open(Game game) {
        Scope scope = new Scope(CURRENT.get(), game);
        CURRENT.set(scope);
        return scope;
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapFor(Game game, String name) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.game != game) return new HashMap<>();
        return (Map<K, V>) scope.maps.computeIfAbsent(name, key -> new HashMap<>());
    }

    public static <T> T checked(String name, Object key, T shared, Supplier<T> fresh) {
        if (!verifying || CURRENT.get() == null) return shared;
        T recomputed = unshared(fresh);
        boolean same = Objects.equals(shared, recomputed);
        synchronized (CHECKS) {
            long[] counts = CHECKS.computeIfAbsent(name, ignored -> new long[2]);
            counts[0]++;
            if (!same) {
                counts[1]++;
                if (MISMATCHES.size() < MISMATCHES_KEPT) {
                    MISMATCHES.add(name + " " + key);
                }
            }
        }
        return shared;
    }

    public static <T> T unshared(Supplier<T> evaluation) {
        Scope scope = CURRENT.get();
        CURRENT.remove();
        try {
            return evaluation.get();
        } finally {
            if (scope != null) CURRENT.set(scope);
        }
    }

    public static boolean verifying() {
        return verifying;
    }

    static void verify(boolean on) {
        verifying = on;
    }

    public static List<String> verificationLines() {
        synchronized (CHECKS) {
            List<String> lines = new ArrayList<>();
            CHECKS.forEach((name, counts) -> lines.add(
                    "TICKMEMO " + name + ": " + counts[0] + " shared answers checked, " + counts[1] + " differed"));
            MISMATCHES.forEach(mismatch -> lines.add("TICKMEMO   " + mismatch));
            return lines;
        }
    }

    public static void resetVerification() {
        synchronized (CHECKS) {
            CHECKS.clear();
            MISMATCHES.clear();
        }
    }

    private static final class Scope implements Open {
        private final Scope previous;
        private final Game game;
        private final Map<String, Map<?, ?>> maps = new HashMap<>();

        private Scope(Scope previous, Game game) {
            this.previous = previous;
            this.game = game;
        }

        @Override
        public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
