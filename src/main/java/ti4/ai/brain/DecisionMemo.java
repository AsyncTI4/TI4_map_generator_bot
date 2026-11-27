package ti4.ai.brain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DecisionMemo {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    public interface Open extends AutoCloseable {
        @Override
        void close();
    }

    public static Open open() {
        return opened(false);
    }

    public static Open openIsolated() {
        return opened(true);
    }

    public static boolean outermost() {
        Scope scope = CURRENT.get();
        return scope != null && scope.previous == null && !scope.isolated;
    }

    public static boolean isolated() {
        Scope scope = CURRENT.get();
        return scope != null && scope.isolated;
    }

    @SuppressWarnings("unchecked")
    public static <T> T remember(Supplier<T> compute, Object... key) {
        Scope scope = CURRENT.get();
        if (scope == null) return compute.get();
        Object cacheKey = Arrays.asList(key);
        Object cached = scope.values.get(cacheKey);
        if (cached != null) return (T) cached;
        T computed = compute.get();
        if (computed != null) scope.values.put(cacheKey, computed);
        return computed;
    }

    private static Open opened(boolean isolated) {
        Scope previous = CURRENT.get();
        Scope scope = new Scope(previous, isolated || previous != null && previous.isolated);
        CURRENT.set(scope);
        return scope;
    }

    private static final class Scope implements Open {
        private final Scope previous;
        private final boolean isolated;
        private final Map<Object, Object> values = new HashMap<>();

        private Scope(Scope previous, boolean isolated) {
            this.previous = previous;
            this.isolated = isolated;
        }

        @Override
        public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
