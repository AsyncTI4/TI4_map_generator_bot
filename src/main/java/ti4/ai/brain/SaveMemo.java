package ti4.ai.brain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class SaveMemo {

    static final int MAX_GAMES = 64;
    private static final Map<String, Scope> SCOPES = new ConcurrentHashMap<>();

    public static <T> T remember(Game game, Object unsavedState, Supplier<T> compute, Object... key) {
        return answer(game, unsavedState, compute, key).reported();
    }

    @SuppressWarnings("unchecked")
    public static <T> Answer<T> answer(Game game, Object unsavedState, Supplier<T> compute, Object... key) {
        if (!DecisionMemo.outermost()) return Answer.of(Reentry.traced(compute));
        Scope scope = scopeFor(game, List.of(game.getLastModifiedDate(), unsavedState));
        List<Object> cacheKey = Arrays.asList(key);
        Answer<T> kept = (Answer<T>) scope.servable(cacheKey);
        if (kept != null) {
            T checked = TickMemo.checked(String.valueOf(key[0]), cacheKey, kept.value(), compute);
            return checked == kept.value() ? kept : new Answer<>(checked, kept.consulted(), kept.cutBy());
        }
        Answer<T> computed = Answer.of(Reentry.traced(compute));
        if (computed.value() != null) scope.keep(cacheKey, computed);
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

    public record Answer<T>(T value, Set<Object> consulted, Set<Object> cutBy) {

        private static <T> Answer<T> of(Reentry.Traced<T> traced) {
            return new Answer<>(traced.value(), traced.consulted(), traced.cutBy());
        }

        public T reported() {
            Reentry.consultedAgain(consulted);
            return value;
        }

        private boolean servable() {
            return cutBy.equals(Reentry.heldAmong(consulted));
        }
    }

    private record Scope(List<Object> stamp, Map<List<Object>, List<Answer<?>>> values) {

        private Scope(List<Object> stamp) {
            this(stamp, new ConcurrentHashMap<>());
        }

        private Answer<?> servable(List<Object> key) {
            List<Answer<?>> variants = values.get(key);
            if (variants == null) return null;
            for (Answer<?> variant : variants) {
                if (variant.servable()) return variant;
            }
            return null;
        }

        private void keep(List<Object> key, Answer<?> answer) {
            List<Answer<?>> variants = values.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
            variants.removeIf(variant -> variant.cutBy().equals(answer.cutBy()));
            variants.add(answer);
        }
    }
}
