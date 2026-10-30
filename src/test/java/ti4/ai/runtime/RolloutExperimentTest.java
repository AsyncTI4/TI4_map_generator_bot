package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.StrategyCardRanking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.RandomHelper;
import ti4.testUtils.BaseTi4Test;

// Branch-and-compare on one decision: the first strategy card pick of a round. Each game plays to that pick, takes a
// snapshot, and for each of the picking seat's top options (by its own card values) restores the snapshot, forces the
// pick, and plays on to the start of round R + 2, several times with different continuation seeds. Before each
// continuation, everything the seat cannot see is resampled (Determinizer): other seats' action cards and unscored
// secrets are dealt again from their decks and the hidden decks are shuffled, so the seat cannot plan around, say, a
// Sabotage it could not know about. That is a determinized rollout in the spirit of information-set Monte Carlo. The
// same continuation seeds are used for every option, so options are compared on common random numbers. Score: the
// seat's victory points minus the best other seat's at the horizon. A rollout takes about 80 seconds; in an async game
// that budget is affordable per decision, so the default runs 12 continuations per option.
// mvn -o test -Dtest=RolloutExperimentTest -Dai.selfplay.rollouts=true
//     [-Dai.selfplay.rollouts.games=6 -Dai.selfplay.rollouts.threads=6 -Dai.selfplay.rollouts.round=2
//      -Dai.selfplay.rollouts.options=3 -Dai.selfplay.rollouts.seeds=12 -Dai.selfplay.rollouts.seed=5000]
@EnabledIfSystemProperty(named = "ai.selfplay.rollouts", matches = "true")
class RolloutExperimentTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("nekro", "sardakk", "hacan", "letnev", "sol", "xxcha");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int GAMES = Integer.getInteger("ai.selfplay.rollouts.games", 6);
    private static final int THREADS = Integer.getInteger("ai.selfplay.rollouts.threads", 6);
    private static final int ROUND = Integer.getInteger("ai.selfplay.rollouts.round", 2);
    private static final int OPTIONS = Integer.getInteger("ai.selfplay.rollouts.options", 3);
    private static final int SEEDS = Integer.getInteger("ai.selfplay.rollouts.seeds", 12);
    private static final long BASE_SEED = Long.getLong("ai.selfplay.rollouts.seed", 5000L);
    private static final String PICK = "scPick_";

    record OptionResult(int initiative, String card, double cardValue, List<Integer> margins, List<Integer> points) {

        double meanMargin() {
            return margins.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        double standardError() {
            double mean = meanMargin();
            double variance = margins.stream()
                            .mapToDouble(margin -> (margin - mean) * (margin - mean))
                            .sum()
                    / Math.max(1, margins.size() - 1);
            return Math.sqrt(variance / Math.max(1, margins.size()));
        }
    }

    record DecisionResult(long seed, String faction, int aiPick, List<OptionResult> options, long wallSeconds) {}

    @Test
    void comparePicksByRollout() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.clamp(THREADS, 1, GAMES));
        List<Future<DecisionResult>> futures = new ArrayList<>();
        for (int slot = 0; slot < GAMES; slot++) {
            long seed = BASE_SEED + slot;
            futures.add(pool.submit(() -> examine(seed)));
        }
        List<DecisionResult> results = new ArrayList<>();
        for (Future<DecisionResult> future : futures) results.add(future.get());
        pool.shutdown();
        report(results);
    }

    private static DecisionResult examine(long seed) throws Exception {
        long wallStart = System.nanoTime();
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(1_790_000_000_000L + seed)) {
            SelfPlayGame selfPlay = new SelfPlayGame(env, "airoll" + seed, (int) seed, seed);
            try {
                selfPlay.setUp(FACTIONS);
                selfPlay.run(RolloutExperimentTest::atFirstPick, 14 * VIRTUAL_DAY, 100_000);
                Game game = GameManager.getManagedGame(selfPlay.name).getGame();
                Player seat = game.getPlayer(game.getActivePlayerID());
                String faction = seat.getFaction();
                List<Integer> options = topOptions(game, seat);
                Map<Integer, Double> values = new LinkedHashMap<>();
                for (int initiative : options)
                    values.put(initiative, StrategyCardRanking.value(game, seat, initiative));
                SelfPlayGame.Snapshot snapshot = selfPlay.snapshot();

                int[] aiPick = {-1};
                List<OptionResult> results = new ArrayList<>();
                for (int initiative : options) {
                    List<Integer> margins = new ArrayList<>();
                    List<Integer> points = new ArrayList<>();
                    for (int k = 0; k < SEEDS; k++) {
                        selfPlay.restore(snapshot);
                        determinize(selfPlay, faction, seed * 1000 + k);
                        env.decisionOverride = (context, decision) -> {
                            if (!context.faction().equals(faction)) return decision;
                            if (!(decision instanceof AiDecision.Press press)
                                    || !press.button().handlerId().startsWith(PICK)) return decision;
                            aiPick[0] = StrategyCardRanking.initiative(press.button());
                            return forced(press, initiative);
                        };
                        selfPlay.run(game2 -> game2.getRound() >= ROUND + 2, 14 * VIRTUAL_DAY, 100_000);
                        env.decisionOverride = null;
                        Game end = GameManager.getManagedGame(selfPlay.name).getGame();
                        Player me = end.getPlayerFromColorOrFaction(faction);
                        int mine = me.getTotalVictoryPoints();
                        int best = end.getRealPlayers().stream()
                                .filter(player -> player != me)
                                .mapToInt(Player::getTotalVictoryPoints)
                                .max()
                                .orElse(0);
                        margins.add(mine - best);
                        points.add(mine);
                    }
                    results.add(new OptionResult(
                            initiative, cardName(game, initiative), values.get(initiative), margins, points));
                }
                long wall = (System.nanoTime() - wallStart) / 1_000_000_000L;
                return new DecisionResult(seed, faction, aiPick[0], results, wall);
            } finally {
                AiRuntime.forget(selfPlay.name);
                GameManager.delete(selfPlay.name);
            }
        }
    }

    private static boolean atFirstPick(Game game) {
        return game.getRound() == ROUND
                && "strategy".equalsIgnoreCase(game.getPhaseOfGame())
                && game.getActivePlayerID() != null
                && game.getRealPlayers().stream()
                        .allMatch(player -> player.getSCs().isEmpty());
    }

    private static List<Integer> topOptions(Game game, Player seat) {
        List<Integer> open = new ArrayList<>(game.getSCList());
        game.getRealPlayers().forEach(player -> open.removeAll(player.getSCs()));
        open.sort(Comparator.comparingDouble((Integer initiative) -> StrategyCardRanking.value(game, seat, initiative))
                .reversed());
        return open.subList(0, Math.min(OPTIONS, open.size()));
    }

    private static AiDecision forced(AiDecision.Press press, int initiative) {
        for (PromptButton button : press.prompt().enabledButtons()) {
            if (button.handlerId().equals(PICK + initiative)) {
                return new AiDecision.Press(press.prompt(), button, "rollout forces strategy card " + initiative);
            }
        }
        return press;
    }

    // Resamples what the deciding seat cannot see, then leaves the game thread seeded for the continuation.
    private static void determinize(SelfPlayGame selfPlay, String faction, long continuationSeed) {
        Random random = new Random(continuationSeed);
        RandomHelper.useForThisThread(random);
        Game game = GameManager.getManagedGame(selfPlay.name).getGame();
        Determinizer.resample(game, game.getPlayerFromColorOrFaction(faction), random);
        GameManager.save(game, "AI rollout determinization");
        selfPlay.forgetObservedGame();
    }

    private static String cardName(Game game, int initiative) {
        return game.getStrategyCardModelByInitiative(initiative)
                .map(model -> model.getName())
                .orElse(String.valueOf(initiative));
    }

    private static void report(List<DecisionResult> results) {
        System.out.println("=== ROLLOUTS: first strategy card pick of round " + ROUND + ", " + OPTIONS + " options x "
                + SEEDS + " continuations to the start of round " + (ROUND + 2) + " ===");
        int aiBest = 0;
        int decided = 0;
        double regret = 0;
        for (DecisionResult result : results) {
            System.out.printf(
                    "seed %d %s (AI picked %d, %ds):%n",
                    result.seed(), result.faction(), result.aiPick(), result.wallSeconds());
            OptionResult best = result.options().stream()
                    .max(Comparator.comparingDouble(OptionResult::meanMargin))
                    .orElseThrow();
            for (OptionResult option : result.options()) {
                System.out.printf(
                        "  %-12s value %5.1f  margin %+5.2f +- %4.2f  points %s%s%s%n",
                        option.card(),
                        option.cardValue(),
                        option.meanMargin(),
                        option.standardError(),
                        option.points(),
                        option.initiative() == result.aiPick() ? "  <- AI" : "",
                        option == best ? "  <- best by rollout" : "");
            }
            OptionResult chosen = result.options().stream()
                    .filter(option -> option.initiative() == result.aiPick())
                    .findFirst()
                    .orElse(null);
            if (chosen != null) {
                decided++;
                if (chosen == best) aiBest++;
                regret += best.meanMargin() - chosen.meanMargin();
            }
        }
        System.out.printf(
                "AI pick was the best rollout option in %d of %d decisions; mean regret %.2f VP margin%n",
                aiBest, decided, decided == 0 ? 0 : regret / decided);
    }
}
