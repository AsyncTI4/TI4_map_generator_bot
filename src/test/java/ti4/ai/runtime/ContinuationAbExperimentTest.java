package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.AiExperiments;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.RandomHelper;
import ti4.testUtils.BaseTi4Test;

// A cheaper A/B than whole games (AI_LOOKAHEAD_PLAN.md step 4). Each game plays with the default brain and is
// snapshotted at the start of the chosen rounds. From each snapshot, each continuation seed plays two arms of HORIZON
// rounds: arm A switches the experiments on for a random half of the seats and off for the rest, arm B the other way
// round. Both arms use the same continuation seed (common random numbers), so they differ only where the experiments
// change a decision. See ContinuationAb for the scoring.
//
// An A/A run (flags none) with common random numbers plays every pair twice identically and scores exactly 0; set
// -Dai.selfplay.continuation.crn=false to give arm B its own seed, which measures the noise without the pairing.
//
// mvn -o test -Dtest=ContinuationAbExperimentTest -Dai.selfplay.continuation=true
//     -Dai.selfplay.continuation.flags=WIN_THREAT_FEASIBLE
//     [-Dai.selfplay.continuation.games=8 -Dai.selfplay.continuation.threads=8
//      -Dai.selfplay.continuation.rounds=2,3,4 -Dai.selfplay.continuation.seeds=4
//      -Dai.selfplay.continuation.horizon=1 -Dai.selfplay.continuation.seed=7000
//      -Dai.selfplay.continuation.crn=true]
@EnabledIfSystemProperty(named = "ai.selfplay.continuation", matches = "true")
class ContinuationAbExperimentTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("nekro", "sardakk", "hacan", "letnev", "sol", "xxcha");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int MAX_TICKS = 100_000;
    private static final Set<AiExperiments.Experiment> FLAGS =
            ContinuationAb.parseFlags(System.getProperty("ai.selfplay.continuation.flags"));
    private static final int GAMES = Integer.getInteger("ai.selfplay.continuation.games", 8);
    private static final int THREADS = Integer.getInteger("ai.selfplay.continuation.threads", 8);
    private static final List<Integer> ROUNDS = Arrays.stream(
                    System.getProperty("ai.selfplay.continuation.rounds", "2,3,4")
                            .split(","))
            .map(String::trim)
            .filter(round -> !round.isEmpty())
            .map(Integer::parseInt)
            .sorted()
            .toList();
    private static final int SEEDS = Integer.getInteger("ai.selfplay.continuation.seeds", 4);
    private static final int HORIZON = Integer.getInteger("ai.selfplay.continuation.horizon", 1);
    private static final long BASE_SEED = Long.getLong("ai.selfplay.continuation.seed", 7000L);
    private static final boolean COMMON_RANDOM_NUMBERS =
            Boolean.parseBoolean(System.getProperty("ai.selfplay.continuation.crn", "true"));

    record Ending(int round, int leader, int total) {}

    record GameResult(List<ContinuationAb.Pair> pairs, List<Ending> endings, long wallSeconds) {}

    @Test
    void compareArmsFromSnapshots() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.clamp(THREADS, 1, GAMES));
        List<Future<GameResult>> futures = new ArrayList<>();
        for (int slot = 0; slot < GAMES; slot++) {
            long seed = BASE_SEED + slot;
            futures.add(pool.submit(() -> examine(seed)));
        }
        List<GameResult> results = new ArrayList<>();
        for (Future<GameResult> future : futures) results.add(future.get());
        pool.shutdown();
        report(results);
    }

    private static GameResult examine(long seed) throws Exception {
        long wallStart = System.nanoTime();
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(1_790_000_000_000L + seed)) {
            SelfPlayGame selfPlay = new SelfPlayGame(env, "aicont" + seed, (int) seed, seed);
            try {
                selfPlay.setUp(FACTIONS);
                Map<Integer, SelfPlayGame.Snapshot> snapshots = new LinkedHashMap<>();
                for (int round : ROUNDS) {
                    SelfPlayGame.Outcome reached =
                            selfPlay.run(game -> game.getRound() >= round, 14 * VIRTUAL_DAY, MAX_TICKS);
                    if (reached.ending() != SelfPlayGame.Ending.STOP_CONDITION) break;
                    snapshots.put(round, selfPlay.snapshot());
                }
                List<ContinuationAb.Pair> pairs = new ArrayList<>();
                List<Ending> endings = new ArrayList<>();
                for (Map.Entry<Integer, SelfPlayGame.Snapshot> snapshot : snapshots.entrySet()) {
                    int round = snapshot.getKey();
                    for (int k = 0; k < SEEDS; k++) {
                        long continuationSeed = seed * 1000 + round * 100L + k;
                        List<Integer> half = ContinuationAb.treatedHalf(FACTIONS.size(), new Random(continuationSeed));
                        ContinuationAb.Arm a = arm(selfPlay, snapshot.getValue(), round, continuationSeed, half, true);
                        endings.add(ending(selfPlay));
                        long secondSeed = COMMON_RANDOM_NUMBERS ? continuationSeed : continuationSeed + 1_000_000_007L;
                        ContinuationAb.Arm b = arm(selfPlay, snapshot.getValue(), round, secondSeed, half, false);
                        endings.add(ending(selfPlay));
                        pairs.add(new ContinuationAb.Pair(seed, round, k, a, b));
                    }
                }
                return new GameResult(pairs, endings, (System.nanoTime() - wallStart) / 1_000_000_000L);
            } finally {
                AiRuntime.forget(selfPlay.name);
                GameManager.delete(selfPlay.name);
            }
        }
    }

    // halfTreated: true treats the seats in half, false treats the others.
    private static ContinuationAb.Arm arm(
            SelfPlayGame selfPlay,
            SelfPlayGame.Snapshot snapshot,
            int round,
            long continuationSeed,
            List<Integer> half,
            boolean halfTreated) {
        selfPlay.restore(snapshot);
        RandomHelper.useForThisThread(new Random(continuationSeed));
        Game game = game(selfPlay);
        Set<String> treated = new LinkedHashSet<>();
        List<Player> seats = game.getRealPlayers();
        for (int index = 0; index < seats.size(); index++) {
            Player seat = seats.get(index);
            if (half.contains(index) == halfTreated) {
                treated.add(seat.getFaction());
                AiExperiments.enable(seat, FLAGS);
            } else if (!FLAGS.isEmpty()) {
                AiExperiments.disable(seat, FLAGS);
            }
        }
        Map<String, Integer> before = points(game);
        GameManager.save(game, "AI continuation A/B arm");
        selfPlay.forgetObservedGame();
        selfPlay.run(next -> next.getRound() >= round + HORIZON, 14 * VIRTUAL_DAY, MAX_TICKS);
        return new ContinuationAb.Arm(treated, ContinuationAb.gains(before, points(game(selfPlay))));
    }

    private static Game game(SelfPlayGame selfPlay) {
        return GameManager.getManagedGame(selfPlay.name).getGame();
    }

    private static Map<String, Integer> points(Game game) {
        Map<String, Integer> points = new LinkedHashMap<>();
        for (Player seat : game.getRealPlayers()) points.put(seat.getFaction(), seat.getTotalVictoryPoints());
        return points;
    }

    private static Ending ending(SelfPlayGame selfPlay) {
        Game game = game(selfPlay);
        Map<String, Integer> points = points(game);
        int leader = points.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int total = points.values().stream().mapToInt(Integer::intValue).sum();
        return new Ending(game.getRound(), leader, total);
    }

    private static void report(List<GameResult> results) {
        List<ContinuationAb.Pair> pairs =
                results.stream().flatMap(result -> result.pairs().stream()).toList();
        List<Ending> endings =
                results.stream().flatMap(result -> result.endings().stream()).toList();
        System.out.printf(
                "=== CONTINUATION A/B %s: snapshots at rounds %s, %d continuations each, horizon %d round(s), %s ===%n",
                FLAGS.isEmpty() ? "none (A/A)" : FLAGS,
                ROUNDS,
                SEEDS,
                HORIZON,
                COMMON_RANDOM_NUMBERS ? "common random numbers" : "independent seeds per arm");
        for (ContinuationAb.Pair pair : pairs) {
            System.out.printf(
                    "CONTPAIR seed=%d round=%d k=%d a=%.3f b=%.3f value=%.3f%s%n",
                    pair.seed(),
                    pair.round(),
                    pair.continuation(),
                    pair.a().difference(),
                    pair.b().difference(),
                    pair.value(),
                    pair.untouched() ? " untouched" : "");
        }
        ContinuationAb.summary(pairs).forEach(System.out::println);
        System.out.printf(
                "Continuation ends: average round %.2f, leader %.2f VP, table %.2f VP over %d arms; wall %d s per game%n",
                endings.stream().mapToInt(Ending::round).average().orElse(0),
                endings.stream().mapToInt(Ending::leader).average().orElse(0),
                endings.stream().mapToInt(Ending::total).average().orElse(0),
                endings.size(),
                (long) results.stream()
                        .mapToLong(GameResult::wallSeconds)
                        .average()
                        .orElse(0));
    }
}
