package ti4.ai.tactical;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner.Candidate;
import ti4.ai.tactical.TacticalPlanner.Context;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class EnemyIntent {

    private static final int MAX_CACHED = 256;
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(WeakReference<Game> game, long modified, Object value) {

        boolean isFor(Game current) {
            return game.get() == current && modified == current.getLastModifiedDate();
        }
    }

    static Optional<List<TacticalPlan>> ranked(Game game, Player enemy) {
        if (ImaginedTurn.underway()) return Optional.empty();
        return Optional.of(remembered(
                game,
                enemy,
                enemy.getFaction(),
                () -> ImaginedTurn.of(enemy, () -> TacticalPlanner.ranked(game, enemy))));
    }

    static OptionalDouble attackWorth(Game game, Player enemy, Tile tile, Player defender) {
        if (ImaginedTurn.underway()) return OptionalDouble.empty();
        return remembered(
                game,
                enemy,
                enemy.getFaction() + "|" + tile.getPosition() + "|" + defender.getFaction(),
                () -> ImaginedTurn.of(enemy, () -> bestAttack(game, enemy, tile, defender)));
    }

    private static String memoryMark(Game game, Player enemy) {
        return AttackLedger.remembers(game, enemy) ? "|" + AiExperiments.Experiment.ATTACK_MEMORY : "";
    }

    @SuppressWarnings("unchecked")
    private static <T> T remembered(Game game, Player enemy, String key, Supplier<T> compute) {
        String fullKey = game.getName() + "|" + key + memoryMark(game, enemy);
        Cached cached = CACHE.get(fullKey);
        if (cached != null && cached.isFor(game)) return (T) cached.value();
        T value = compute.get();
        if (CACHE.size() > MAX_CACHED) CACHE.clear();
        CACHE.put(fullKey, new Cached(new WeakReference<>(game), game.getLastModifiedDate(), value));
        return value;
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
