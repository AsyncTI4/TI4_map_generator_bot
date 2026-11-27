package ti4.ai.brain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class SaveMemo {

    static final int MAX_GAMES = 64;
    private static final Map<String, Scope> SCOPES = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T remember(Game game, Object unsavedState, Supplier<T> compute, Object... key) {
        if (!DecisionMemo.outermost()) return compute.get();
        Scope scope = scopeFor(game, List.of(game.getLastModifiedDate(), unsavedState));
        List<Object> cacheKey = Arrays.asList(key);
        Object cached = scope.values.get(cacheKey);
        if (cached != null) return TickMemo.checked(String.valueOf(key[0]), cacheKey, (T) cached, compute);
        T computed = compute.get();
        if (computed != null) scope.values.put(cacheKey, computed);
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

    private record Scope(List<Object> stamp, Map<List<Object>, Object> values) {

        private Scope(List<Object> stamp) {
            this(stamp, new ConcurrentHashMap<>());
        }
    }
}
