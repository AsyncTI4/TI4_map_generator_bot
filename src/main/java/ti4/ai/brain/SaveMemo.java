package ti4.ai.brain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.Reentry.Traced;
import ti4.game.Game;

@UtilityClass
public class SaveMemo {

    static final int MAX_GAMES = 64;
    private static final Map<String, Scope> SCOPES = new ConcurrentHashMap<>();

    public static <T> T remember(Game game, Object unsavedState, Supplier<T> compute, Object... key) {
        return answer(game, unsavedState, compute, key).reported();
    }

    @SuppressWarnings("unchecked")
    public static <T> Traced<T> answer(Game game, Object unsavedState, Supplier<T> compute, Object... key) {
        if (!DecisionMemo.outermost()) return Reentry.traced(compute);
        Scope scope = scopeFor(game, List.of(game.getLastModifiedDate(), unsavedState));
        List<Object> cacheKey = Arrays.asList(key);
        Traced<T> kept = (Traced<T>) scope.servable(cacheKey);
        if (kept != null) {
            TickMemo.checked(String.valueOf(key[0]), cacheKey, kept.value(), compute);
            return kept;
        }
        Traced<T> computed = Reentry.traced(compute);
        if (computed.keepable()) scope.keep(cacheKey, computed);
        return computed;
    }

    public static void forget(String gameName) {
        SCOPES.remove(gameName);
    }

    static int gamesKept() {
        return SCOPES.size();
    }

    static void clear() {
        SCOPES.clear();
    }

    private static Scope scopeFor(Game game, List<Object> stamp) {
        if (SCOPES.size() >= MAX_GAMES && !SCOPES.containsKey(game.getName())) SCOPES.clear();
        return SCOPES.compute(
                game.getName(), (name, old) -> old != null && old.stamp.equals(stamp) ? old : new Scope(stamp));
    }

    private record Scope(List<Object> stamp, Map<List<Object>, List<Traced<?>>> values) {

        private Scope(List<Object> stamp) {
            this(stamp, new ConcurrentHashMap<>());
        }

        private Traced<?> servable(List<Object> key) {
            List<Traced<?>> variants = values.get(key);
            if (variants == null) return null;
            for (Traced<?> variant : variants) {
                if (variant.servable()) return variant;
            }
            return null;
        }

        private void keep(List<Object> key, Traced<?> answer) {
            List<Traced<?>> variants = values.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
            variants.removeIf(variant -> variant.cutBy().equals(answer.cutBy()));
            variants.add(answer);
        }
    }
}
