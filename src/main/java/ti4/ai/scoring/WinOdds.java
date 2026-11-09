package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.secrets.SecretOdds.CardChance;
import ti4.ai.tactical.StyxTake;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.service.info.ListPlayerInfoService;
import ti4.website.model.WebScoreBreakdown.EntryType;

@UtilityClass
public class WinOdds {

    public static final double MAJORITY = 0.5;
    private static final int ONE_POINT = 1;
    private static final int STATUS_PUBLIC_SCORES = 1;
    private static final int IMPERIAL_PUBLIC_SCORES = 1;
    private static final int STATUS_SECRET_SCORES = 1;

    public record Odds(double duringAction, double byStatusScoring, double byStatusEnd) {

        static final Odds CERTAIN = new Odds(1, 1, 1);
    }

    public static Odds of(Game game, Player viewer, Player rival) {
        return DecisionMemo.remember(
                () -> compute(game, viewer, rival),
                "win.odds",
                viewer,
                rival,
                DecidingSeat.knowsHandOf(viewer),
                game.getRound());
    }

    public static double reachesGoal(Game game, Player viewer, Player rival) {
        if (rival.isNeutral() || rival.isEliminated() || game.getVp() <= 0) return 0;
        return of(game, viewer, rival).byStatusEnd();
    }

    public static double finishesBefore(Game game, Player viewer, Player rival, OptionalInt mine) {
        Odds odds = of(game, viewer, rival);
        if (mine.isEmpty()) return odds.byStatusEnd();
        int myFinish = mine.getAsInt();
        if (myFinish <= WinRace.DURING_ACTION_PHASE) return 0;
        boolean scoresEarlier = WinRace.statusFinish(rival) < myFinish;
        return odds.duringAction() + (scoresEarlier ? odds.byStatusScoring() - odds.duringAction() : 0);
    }

    private static Odds compute(Game game, Player viewer, Player rival) {
        int missing = game.getVp() - rival.getTotalVictoryPoints();
        if (missing <= 0) return Odds.CERTAIN;
        boolean imperial = ScoringStakes.holdsUnplayedImperial(game, rival);
        boolean mecatol = rival.controlsMecatol(true);
        Grid grid = Grid.start(missing);
        grid = grid.with(publics(game, rival, imperial, missing));
        grid = grid.with(secrets(game, viewer, rival, missing));
        if (imperial && mecatol) grid = grid.with(Grid.certain(missing, ONE_POINT, 0));
        grid = grid.with(Grid.chance(missing, custodiansPoints(imperial, mecatol), 0, custodians(game, viewer, rival)));
        grid = grid.with(Grid.chance(missing, ONE_POINT, 0, StyxTake.thisRound(game, rival)));
        int latvinia = ScoringPaths.readyPoints(game, rival, EntryType.LATVINIA);
        if (latvinia > 0) grid = grid.with(Grid.certain(missing, latvinia, 0));
        int crown = ScoringPaths.readyPoints(game, rival, EntryType.CROWN);
        return new Odds(grid.actionAtLeast(missing), grid.totalAtLeast(missing, 0), grid.totalAtLeast(missing, crown));
    }

    private static int custodiansPoints(boolean imperial, boolean mecatol) {
        return ONE_POINT + (imperial && !mecatol ? ONE_POINT : 0);
    }

    private static double custodians(Game game, Player viewer, Player rival) {
        if (!CustodiansRace.open(game)) return 0;
        CustodiansRace.Snapshot race = CustodiansRace.of(game, viewer);
        if (!race.canLandThisRound(rival)) return 0;
        OptionalInt arrival = race.arrival(rival);
        if (arrival.isEmpty() || arrival.getAsInt() != game.getRound()) return 0;
        return race.winChance(rival, arrival.getAsInt(), CustodiansRace.Order.KNOWN);
    }

    private record Candidate(int points, double chance) {}

    private static Grid publics(Game game, Player rival, boolean imperial, int missing) {
        List<Candidate> candidates = publicCandidates(game, rival);
        int picks = STATUS_PUBLIC_SCORES + (imperial ? IMPERIAL_PUBLIC_SCORES : 0);
        double[][][] byPicks = new double[picks + 1][missing + 1][missing + 1];
        byPicks[0][0][0] = 1;
        for (Candidate candidate : candidates) {
            double[][][] next = new double[picks + 1][missing + 1][missing + 1];
            for (int taken = 0; taken <= picks; taken++) {
                for (int action = 0; action <= missing; action++) {
                    for (int status = 0; status <= missing; status++) {
                        double p = byPicks[taken][action][status];
                        if (p == 0) continue;
                        if (taken == picks) {
                            next[taken][action][status] += p;
                            continue;
                        }
                        next[taken][action][status] += p * (1 - candidate.chance());
                        boolean duringAction = imperial && taken == 0;
                        int a = duringAction ? cap(action + candidate.points(), missing) : action;
                        int s = duringAction ? status : cap(status + candidate.points(), missing);
                        next[taken + 1][a][s] += p * candidate.chance();
                    }
                }
            }
            byPicks = next;
        }
        Grid grid = Grid.empty(missing);
        for (double[][] layer : byPicks) grid.addAll(layer);
        return grid;
    }

    private static List<Candidate> publicCandidates(Game game, Player rival) {
        if (!Helper.canPlayerScorePOs(game, rival)) return List.of();
        List<Candidate> candidates = new ArrayList<>();
        Wallet wallet = WinRace.projectedWallet(game, rival);
        int bestSpend = 0;
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (ObjectivePolicy.hasScored(game, rival, id)) continue;
            boolean ready = WinRace.qualifies(game, rival, wallet, id);
            if (ObjectiveCatalog.isSpend(id)) {
                if (ready) bestSpend = Math.max(bestSpend, ObjectivePolicy.victoryPoints(id));
                continue;
            }
            if (ready) {
                candidates.add(new Candidate(ObjectivePolicy.victoryPoints(id), 1));
                continue;
            }
            int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
            if (threshold <= 0) continue;
            int progress = ListPlayerInfoService.getPlayerProgressOnObjective(id, game, rival);
            if (progress >= threshold) continue;
            double chance = SecretOdds.readinessAt(progress, threshold);
            if (chance > 0) candidates.add(new Candidate(ObjectivePolicy.victoryPoints(id), chance));
        }
        if (bestSpend > 0) candidates.add(new Candidate(bestSpend, 1));
        candidates.sort(Comparator.comparingInt(Candidate::points)
                .reversed()
                .thenComparing(Comparator.comparingDouble(Candidate::chance).reversed()));
        return candidates;
    }

    private static Grid secrets(Game game, Player viewer, Player rival, int missing) {
        double[][] byStatusTaken = new double[STATUS_SECRET_SCORES + 1][missing + 1];
        byStatusTaken[0][0] = 1;
        for (CardChance card : SecretOdds.readyByCard(game, viewer, rival)) {
            double[][] next = new double[STATUS_SECRET_SCORES + 1][missing + 1];
            double none = 1 - card.status() - card.action();
            for (int taken = 0; taken <= STATUS_SECRET_SCORES; taken++) {
                for (int action = 0; action <= missing; action++) {
                    double p = byStatusTaken[taken][action];
                    if (p == 0) continue;
                    next[taken][action] += p * none;
                    next[taken][cap(action + ONE_POINT, missing)] += p * card.action();
                    next[Math.min(STATUS_SECRET_SCORES, taken + 1)][action] += p * card.status();
                }
            }
            byStatusTaken = next;
        }
        Grid grid = Grid.empty(missing);
        for (int taken = 0; taken <= STATUS_SECRET_SCORES; taken++) {
            for (int action = 0; action <= missing; action++) {
                grid.add(action, cap(taken, missing), byStatusTaken[taken][action]);
            }
        }
        return grid;
    }

    private static int cap(int points, int missing) {
        return Math.min(points, missing);
    }

    private static final class Grid {

        private final int missing;
        private final double[][] chances;

        private Grid(int missing) {
            this.missing = missing;
            this.chances = new double[missing + 1][missing + 1];
        }

        static Grid empty(int missing) {
            return new Grid(missing);
        }

        static Grid start(int missing) {
            return certain(missing, 0, 0);
        }

        static Grid certain(int missing, int action, int status) {
            return chance(missing, action, status, 1);
        }

        static Grid chance(int missing, int action, int status, double chance) {
            Grid grid = new Grid(missing);
            grid.add(0, 0, 1 - chance);
            grid.add(cap(action, missing), cap(status, missing), chance);
            return grid;
        }

        void add(int action, int status, double chance) {
            chances[action][status] += chance;
        }

        void addAll(double[][] layer) {
            for (int action = 0; action <= missing; action++) {
                for (int status = 0; status <= missing; status++) chances[action][status] += layer[action][status];
            }
        }

        Grid with(Grid other) {
            Grid combined = new Grid(missing);
            for (int a1 = 0; a1 <= missing; a1++) {
                for (int s1 = 0; s1 <= missing; s1++) {
                    double p1 = chances[a1][s1];
                    if (p1 == 0) continue;
                    for (int a2 = 0; a2 <= missing; a2++) {
                        for (int s2 = 0; s2 <= missing; s2++) {
                            double p2 = other.chances[a2][s2];
                            if (p2 == 0) continue;
                            combined.add(cap(a1 + a2, missing), cap(s1 + s2, missing), p1 * p2);
                        }
                    }
                }
            }
            return combined;
        }

        double actionAtLeast(int points) {
            double total = 0;
            for (int status = 0; status <= missing; status++) total += chances[cap(points, missing)][status];
            return total;
        }

        double totalAtLeast(int points, int late) {
            double total = 0;
            for (int action = 0; action <= missing; action++) {
                for (int status = 0; status <= missing; status++) {
                    if (action + status + late >= points) total += chances[action][status];
                }
            }
            return total;
        }
    }
}
