package ti4.ai.tactical;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.eval.MovementAssumptions;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner.Candidate;
import ti4.ai.tactical.TacticalPlanner.Context;
import ti4.ai.trace.ShareCensus;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class EnemyIntent {

    static final int MAX_CACHED = 4096;
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(WeakReference<Game> game, long modified, Object value) {

        boolean isFor(Game current) {
            return game.get() == current && modified == current.getLastModifiedDate();
        }

        boolean stale() {
            Game current = game.get();
            return current == null || !isFor(current);
        }
    }

    static Optional<List<TacticalPlan>> ranked(Game game, Player enemy) {
        try (Thinking.Span timing = Thinking.measure("enemy.ranked")) {
            if (ImaginedTurn.underway()) return Optional.empty();
            return Optional.of(remembered(
                    game, enemy.getFaction(), () -> imagined(enemy, () -> TacticalPlanner.ranked(game, enemy))));
        }
    }

    static OptionalDouble attackWorth(Game game, Player enemy, Tile tile, Player defender) {
        if (ImaginedTurn.underway()) return OptionalDouble.empty();
        return remembered(
                game,
                enemy.getFaction() + "|" + tile.getPosition() + "|" + defender.getFaction(),
                () -> imagined(enemy, () -> bestAttack(game, enemy, tile, defender)));
    }

    static <T> T imagined(Player enemy, Supplier<T> turn) {
        return ImaginedTurn.of(enemy, () -> DecidingSeat.of(enemy, turn));
    }

    @SuppressWarnings("unchecked")
    static <T> T remembered(Game game, String key, Supplier<T> compute) {
        String fullKey = game.getName() + "|" + key + "|" + MovementAssumptions.key() + "|"
                + SkippedSpaceCombat.assumedFaction();
        Cached cached = CACHE.get(fullKey);
        if (cached != null && cached.isFor(game)) return (T) cached.value();
        T value = ShareCensus.perSave("enemy.intent", game, fullKey, compute);
        if (CACHE.size() >= MAX_CACHED) evict();
        CACHE.put(fullKey, new Cached(new WeakReference<>(game), game.getLastModifiedDate(), value));
        return value;
    }

    static void evict() {
        CACHE.values().removeIf(Cached::stale);
        if (CACHE.size() >= MAX_CACHED) CACHE.clear();
    }

    static int cachedCount() {
        return CACHE.size();
    }

    static void forget() {
        CACHE.clear();
    }

    private static OptionalDouble bestAttack(Game game, Player enemy, Tile tile, Player defender) {
        Context context = new Context(game, enemy);
        return TacticalPlanner.finalPlan(
                        TacticalPlanner.candidatePool(context, tile, defender),
                        candidate -> worth(context, tile, candidate))
                .map(plan -> OptionalDouble.of(plan.score()))
                .orElse(OptionalDouble.empty());
    }

    private static Optional<TacticalPlan> worth(Context context, Tile tile, Candidate candidate) {
        if (candidate.estimate().spaceWin() < candidate.strike().rules().landMinWin()) return Optional.empty();
        double score = context.offense()
                .evaluate(candidate.strike(), candidate.estimate(), candidate.terms(), true)
                .score();
        return Optional.of(new TacticalPlan(Kind.ATTACK, tile.getPosition(), candidate.moves(), Map.of(), score));
    }
}
