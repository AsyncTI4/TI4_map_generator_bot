package ti4.ai.strategy;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.Reentry;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.eval.MovementAssumptions;
import ti4.ai.eval.Threats;
import ti4.ai.tactical.DefenseOutlook;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

record PlanInputs(
        Game game,
        Player seat,
        PlanMoment moment,
        List<TacticalPlan> ranked,
        Set<String> threatened,
        int reinforcements,
        Map<String, List<TacticalPlan>> freeing,
        Map<Integer, CardPlan> plans,
        Map<String, Double> lockExposures,
        CustodiansRace.Snapshot race,
        boolean planless) {

    private static final int MAX_CACHED = 32;
    private static final String DECISION_MEMO = "strategy.planInputs";
    private static final ThreadLocal<Set<String>> COMPUTING = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Boolean> RANKING_FREED = ThreadLocal.withInitial(() -> false);
    private static final String RANKING_FREED_GUARD = "strategy.rankingFreed";
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > MAX_CACHED;
        }
    };

    private record Cached(WeakReference<Game> game, long modified, PlanInputs inputs) {

        boolean isFor(Game current) {
            return game.get() == current && modified == current.getLastModifiedDate();
        }
    }

    static PlanInputs of(Game game, Player seat, PlanMoment moment) {
        String key = game.getName() + "|" + seat.getUserID() + "|" + moment + viewerKey(game, seat)
                + ImaginedTurn.cacheMark(seat);
        if (Reentry.cutsShort(computingGuard(key), COMPUTING.get().contains(key))) {
            return withoutPlans(game, seat, moment);
        }
        return DecisionMemo.remember(
                () -> cachedOrComputed(game, seat, moment, key), DECISION_MEMO, game, game.getLastModifiedDate(), key);
    }

    private static PlanInputs cachedOrComputed(Game game, Player seat, PlanMoment moment, String key) {
        synchronized (CACHE) {
            Cached cached = CACHE.get(key);
            if (cached != null && cached.isFor(game)) return cached.inputs();
        }
        String guard = computingGuard(key);
        COMPUTING.get().add(key);
        PlanInputs inputs;
        Reentry.enter(guard);
        try {
            inputs = compute(game, seat, moment);
        } finally {
            COMPUTING.get().remove(key);
            Reentry.exit(guard);
        }
        if (DecisionMemo.isolated()) return inputs;
        synchronized (CACHE) {
            CACHE.put(key, new Cached(new WeakReference<>(game), game.getLastModifiedDate(), inputs));
        }
        return inputs;
    }

    private static String computingGuard(String key) {
        return "strategy.planInputs|" + key;
    }

    static void clearShared() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static String viewerKey(Game game, Player seat) {
        return "|" + Objects.toString(DecidingSeat.currentUserId(), "")
                + (TacticalPlanner.poolsMoveBoosts(game, seat) ? "|pooledBoosts" : "")
                + "|" + MovementAssumptions.key();
    }

    private static PlanInputs compute(Game game, Player seat, PlanMoment moment) {
        try (Thinking.Span ignored = Thinking.enter("strategy.planInputs", () -> seat.getFaction() + " " + moment)) {
            return assemble(game, seat, moment, TacticalPlanner.ranked(game, seat), false);
        }
    }

    private static PlanInputs withoutPlans(Game game, Player seat, PlanMoment moment) {
        try (Thinking.Span ignored = Thinking.enter("strategy.planInputs.reentered")) {
            return assemble(game, seat, moment, List.of(), true);
        }
    }

    private static PlanInputs assemble(
            Game game, Player seat, PlanMoment moment, List<TacticalPlan> ranked, boolean planless) {
        return new PlanInputs(
                game,
                seat,
                moment,
                ranked,
                Threats.reachableByEnemies(game, seat),
                StrategyCardRules.reinforcements(game, seat),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                CustodiansRace.of(game, seat),
                planless);
    }

    int round() {
        return game.getRound();
    }

    boolean nearWin() {
        return seat.getTotalVictoryPoints() >= game.getVp() - CardValueScale.NEAR_WIN_GAP;
    }

    boolean agendaPhaseActive() {
        return game.isCustodiansScored();
    }

    boolean isSpeaker() {
        return seat.getUserID().equals(game.getSpeakerUserID());
    }

    boolean isThreatened(@Nullable Tile tile) {
        return tile != null && threatened.contains(tile.getPosition());
    }

    double lockExposure(Tile tile) {
        String position = tile.getPosition();
        Double known = lockExposures.get(position);
        if (known != null) return known;
        double exposure = new DefenseOutlook(game, seat).lockCost(tile);
        Double raced = lockExposures.putIfAbsent(position, exposure);
        return raced != null ? raced : exposure;
    }

    double bestScore() {
        return TacticalPlanner.bestScore(ranked);
    }

    List<TacticalPlan> rankedFreeing(String position) {
        if (planless) return List.of();
        return rememberedFreeing(freeing, position, freed -> TacticalPlanner.rankedFreeing(game, seat, freed));
    }

    static List<TacticalPlan> rememberedFreeing(
            Map<String, List<TacticalPlan>> freeing, String position, Function<String, List<TacticalPlan>> rank) {
        List<TacticalPlan> known = freeing.get(position);
        if (known != null) return known;
        if (Reentry.cutsShort(RANKING_FREED_GUARD, RANKING_FREED.get())) return List.of();
        RANKING_FREED.set(true);
        Reentry.enter(RANKING_FREED_GUARD);
        try {
            List<TacticalPlan> ranked = rank.apply(position);
            List<TacticalPlan> raced = freeing.putIfAbsent(position, ranked);
            return raced != null ? raced : ranked;
        } finally {
            RANKING_FREED.set(false);
            Reentry.exit(RANKING_FREED_GUARD);
        }
    }

    double unblockGain(String position) {
        return TacticalPlanner.bestScore(rankedFreeing(position)) - bestScore();
    }
}
