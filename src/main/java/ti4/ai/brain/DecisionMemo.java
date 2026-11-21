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
        Scope scope = new Scope(CURRENT.get());
        CURRENT.set(scope);
        return scope;
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

    private static final class Scope implements Open {
        private final Scope previous;
        private final Map<Object, Object> values = new HashMap<>();

        private Scope(Scope previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
