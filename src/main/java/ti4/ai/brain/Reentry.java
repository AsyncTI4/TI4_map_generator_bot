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
        Trace innermost = TRACES.get().peek();
        if (innermost != null) innermost.consult(guard, held);
        return held;
    }

    public static <T> Traced<T> traced(Supplier<T> compute) {
        Trace trace = new Trace(Set.copyOf(HELD.get().keySet()));
        Deque<Trace> traces = TRACES.get();
        traces.push(trace);
        try {
            T value = compute.get();
            return new Traced<>(value, Set.copyOf(trace.consulted), Set.copyOf(trace.cutBy), trace.tainted);
        } finally {
            traces.remove(trace);
            Trace enclosing = traces.peek();
            if (enclosing != null) enclosing.absorb(trace);
        }
    }

    static Set<Object> heldAmong(Set<Object> guards) {
        Map<Object, Integer> held = HELD.get();
        if (held.isEmpty() || guards.isEmpty()) return Set.of();
        return guards.stream().filter(held::containsKey).collect(Collectors.toUnmodifiableSet());
    }

    static boolean idle() {
        return HELD.get().isEmpty() && TRACES.get().isEmpty();
    }

    private static void report(Traced<?> answer) {
        Trace innermost = TRACES.get().peek();
        if (innermost == null) return;
        Map<Object, Integer> held = HELD.get();
        for (Object guard : answer.consulted()) innermost.consult(guard, held.containsKey(guard));
        if (answer.tainted() || !answer.servable()) innermost.tainted = true;
    }

    public record Traced<T>(T value, Set<Object> consulted, Set<Object> cutBy, boolean tainted) {

        public boolean servable() {
            return cutBy.equals(heldAmong(consulted));
        }

        public boolean keepable() {
            return value != null && !tainted;
        }

        public T reported() {
            report(this);
            return value;
        }
    }

    private static final class Trace {

        private final Set<Object> heldAtStart;
        private final Set<Object> consulted = new HashSet<>();
        private final Set<Object> cutBy = new HashSet<>();
        private boolean tainted;

        private Trace(Set<Object> heldAtStart) {
            this.heldAtStart = heldAtStart;
        }

        private void consult(Object guard, boolean held) {
            consulted.add(guard);
            if (held && heldAtStart.contains(guard)) cutBy.add(guard);
        }

        private void absorb(Trace inner) {
            consulted.addAll(inner.consulted);
            for (Object guard : inner.cutBy) {
                if (heldAtStart.contains(guard)) cutBy.add(guard);
            }
            tainted |= inner.tainted;
        }
    }
}
