package ti4.ai.brain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Reentry {

    private static final ThreadLocal<Map<Object, Integer>> HELD = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Deque<Trace>> TRACES = ThreadLocal.withInitial(ArrayDeque::new);

    public static void enter(Object guard) {
        HELD.get().merge(guard, 1, Integer::sum);
    }

    public static void exit(Object guard) {
        HELD.get().computeIfPresent(guard, (held, depth) -> depth > 1 ? depth - 1 : null);
    }

    public static boolean cutsShort(Object guard, boolean held) {
        for (Trace trace : TRACES.get()) trace.consult(guard, held);
        return held;
    }

    static Set<Object> heldAmong(Set<Object> guards) {
        Map<Object, Integer> held = HELD.get();
        if (held.isEmpty()) return Set.of();
        return guards.stream().filter(held::containsKey).collect(Collectors.toUnmodifiableSet());
    }

    static void consultedAgain(Set<Object> guards) {
        if (TRACES.get().isEmpty()) return;
        Map<Object, Integer> held = HELD.get();
        for (Object guard : guards) cutsShort(guard, held.containsKey(guard));
    }

    static <T> Traced<T> traced(Supplier<T> compute) {
        Trace trace = new Trace(Set.copyOf(HELD.get().keySet()));
        Deque<Trace> traces = TRACES.get();
        traces.push(trace);
        try {
            T value = compute.get();
            return new Traced<>(value, Set.copyOf(trace.consulted), Set.copyOf(trace.cutBy));
        } finally {
            traces.remove(trace);
        }
    }

    static boolean idle() {
        return HELD.get().isEmpty() && TRACES.get().isEmpty();
    }

    record Traced<T>(T value, Set<Object> consulted, Set<Object> cutBy) {}

    private static final class Trace {

        private final Set<Object> heldAtStart;
        private final Set<Object> consulted = new HashSet<>();
        private final Set<Object> cutBy = new HashSet<>();

        private Trace(Set<Object> heldAtStart) {
            this.heldAtStart = heldAtStart;
        }

        private void consult(Object guard, boolean held) {
            consulted.add(guard);
            if (held && heldAtStart.contains(guard)) cutBy.add(guard);
        }
    }
}
