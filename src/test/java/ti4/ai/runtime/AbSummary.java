package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import ti4.ai.AiExperiments;

// The result of a mixed-brain A/B batch (-Dai.selfplay.ab=...). In each game half of the seats play with the
// experiments switched off (control) and half with them on (treatment); the paired game of the same seed swaps the
// halves, so faction, seat position, map and dice luck cancel out across the pair.
//
// Per game it measures the treatment seats' average victory points minus the control seats', and the treatment share
// of the win (the seats on the highest score split it). Seats in one game are correlated, so the standard errors are
// taken over pairs (complete pairs only), not seats. Under the null hypothesis the win share is 0.5 and the point
// difference 0.
final class AbSummary {

    private AbSummary() {}

    record GameResult(
            int slot,
            long seed,
            String ending,
            int round,
            double treatmentPoints,
            double controlPoints,
            double treatmentWinShare,
            boolean completed) {

        GameResult {
            if (!completed) treatmentWinShare = Double.NaN;
        }

        double difference() {
            return treatmentPoints - controlPoints;
        }
    }

    // A mean with its standard error over n values (NaN error below two values).
    record Stat(double mean, double error, int n) {

        static Stat of(List<Double> values) {
            int n = values.size();
            if (n == 0) return new Stat(Double.NaN, Double.NaN, 0);
            double mean =
                    values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (n == 1) return new Stat(mean, Double.NaN, 1);
            double variance = values.stream()
                            .mapToDouble(value -> (value - mean) * (value - mean))
                            .sum()
                    / (n - 1);
            return new Stat(mean, Math.sqrt(variance / n), n);
        }
    }

    // The paired estimates of one batch: what the run catalog (AbRecord) stores and the console summary prints.
    record Estimate(
            List<GameResult> games,
            int finishedGames,
            int pairs,
            long unfinishedGames,
            Stat pointDifference,
            Stat winShare,
            double treatmentPoints,
            double controlPoints) {}

    static Estimate estimate(List<SelfPlayGame.Outcome> outcomes, Predicate<SelfPlayGame.Ending> complete) {
        List<GameResult> results =
                outcomes.stream().map(outcome -> result(outcome, complete)).toList();
        Map<Long, List<GameResult>> pairs = new TreeMap<>();
        for (GameResult game : results)
            pairs.computeIfAbsent(game.seed(), seed -> new ArrayList<>()).add(game);
        List<Double> pairDifferences = new ArrayList<>();
        List<Double> pairWinShares = new ArrayList<>();
        List<GameResult> completedResults = new ArrayList<>();
        for (List<GameResult> pair : pairs.values()) {
            if (!completedPair(pair)) continue;
            completedResults.addAll(pair);
            pairDifferences.add((pair.get(0).difference() + pair.get(1).difference()) / 2);
            pairWinShares.add((pair.get(0).treatmentWinShare() + pair.get(1).treatmentWinShare()) / 2);
        }
        return new Estimate(
                results,
                completedResults.size(),
                pairDifferences.size(),
                results.stream().filter(game -> !game.completed()).count(),
                Stat.of(pairDifferences),
                Stat.of(pairWinShares),
                completedResults.stream()
                        .mapToDouble(GameResult::treatmentPoints)
                        .average()
                        .orElse(0),
                completedResults.stream()
                        .mapToDouble(GameResult::controlPoints)
                        .average()
                        .orElse(0));
    }

    static GameResult result(SelfPlayGame.Outcome outcome, Predicate<SelfPlayGame.Ending> complete) {
        Map<String, Integer> points = outcome.victoryPoints();
        Set<String> control = outcome.controlSeats();
        int best = points.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double treatmentSum = 0;
        double controlSum = 0;
        int treatmentSeats = 0;
        int controlSeats = 0;
        int winners = 0;
        int treatmentWinners = 0;
        for (Map.Entry<String, Integer> seat : points.entrySet()) {
            boolean isControl = control.contains(seat.getKey());
            if (isControl) {
                controlSum += seat.getValue();
                controlSeats++;
            } else {
                treatmentSum += seat.getValue();
                treatmentSeats++;
            }
            if (seat.getValue() == best) {
                winners++;
                if (!isControl) treatmentWinners++;
            }
        }
        return new GameResult(
                outcome.slot(),
                SelfPlayGame.seedFor(outcome.slot()),
                outcome.ending().name(),
                outcome.round(),
                treatmentSeats == 0 ? 0 : treatmentSum / treatmentSeats,
                controlSeats == 0 ? 0 : controlSum / controlSeats,
                winners == 0 ? 0.5 : (double) treatmentWinners / winners,
                complete.test(outcome.ending()));
    }

    // Full games count only when they ended by the rules (Ending.finished); a batch that stops after a fixed number
    // of rounds passes its own test, so its round-limited endings still give paired estimates.
    static void print(List<SelfPlayGame.Outcome> outcomes, Set<AiExperiments.Experiment> experiments) {
        print(outcomes, experiments, SelfPlayGame.Ending::finished);
    }

    static void print(
            List<SelfPlayGame.Outcome> outcomes,
            Set<AiExperiments.Experiment> experiments,
            Predicate<SelfPlayGame.Ending> complete) {
        Estimate estimate = estimate(outcomes, complete);
        for (GameResult game : estimate.games()) {
            System.out.printf(
                    "ABGAME slot=%d seed=%d ending=%s round=%d treatment=%.3f control=%.3f diff=%.3f winshare=%.3f%n",
                    game.slot(),
                    game.seed(),
                    game.ending(),
                    game.round(),
                    game.treatmentPoints(),
                    game.controlPoints(),
                    game.difference(),
                    game.treatmentWinShare());
        }
        System.out.printf(
                "A/B unfinished games: %d; their pairs are excluded from paired estimates%n",
                estimate.unfinishedGames());
        System.out.printf(
                "A/B %s: %d finished games in %d complete pairs; treatment minus control victory points %s; treatment win share"
                        + " %s (null 0.500); treatment %.2f vs control %.2f average points%n",
                experiments.isEmpty() ? "none (A/A)" : experiments,
                estimate.finishedGames(),
                estimate.pairs(),
                meanAndError(estimate.pointDifference(), 0),
                meanAndError(estimate.winShare(), 0.5),
                estimate.treatmentPoints(),
                estimate.controlPoints());
        perFaction(outcomes.stream()
                        .map(outcome -> new Seating(
                                SelfPlayGame.seedFor(outcome.slot()),
                                complete.test(outcome.ending()),
                                outcome.victoryPoints(),
                                outcome.controlSeats()))
                        .toList())
                .forEach(System.out::println);
    }

    // One game of a pair as the per-faction comparison needs it: who scored what, and which factions were control.
    record Seating(long seed, boolean completed, Map<String, Integer> points, Set<String> control) {

        double winShare(String faction) {
            int best =
                    points.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            long winners =
                    points.values().stream().filter(value -> value == best).count();
            return points.getOrDefault(faction, -1) == best && winners > 0 ? 1.0 / winners : 0;
        }
    }

    // Per faction, over complete pairs where it played treatment in one game and control in the other: its own
    // victory points as treatment minus as control. A faction effect shows here at full size, while the table line
    // above dilutes it across every seat. A faction seated on the same side in both games of a pair (a seating that
    // does not alternate) or in only one of them is counted apart and left out of the estimate.
    static List<String> perFaction(List<Seating> games) {
        Map<Long, List<Seating>> pairs = new TreeMap<>();
        for (Seating game : games)
            pairs.computeIfAbsent(game.seed(), seed -> new ArrayList<>()).add(game);
        Map<String, List<Double>> differences = new TreeMap<>();
        Map<String, List<Double>> treatmentWins = new TreeMap<>();
        Map<String, List<Double>> controlWins = new TreeMap<>();
        Map<String, Integer> sameSide = new TreeMap<>();
        Map<String, Integer> unpaired = new TreeMap<>();
        for (List<Seating> pair : pairs.values()) {
            if (pair.size() != 2 || !pair.stream().allMatch(Seating::completed)) continue;
            Seating first = pair.get(0);
            Seating second = pair.get(1);
            Set<String> factions = new java.util.TreeSet<>(first.points().keySet());
            factions.addAll(second.points().keySet());
            for (String faction : factions) {
                if (!first.points().containsKey(faction) || !second.points().containsKey(faction)) {
                    unpaired.merge(faction, 1, Integer::sum);
                    continue;
                }
                boolean firstTreated = !first.control().contains(faction);
                boolean secondTreated = !second.control().contains(faction);
                if (firstTreated == secondTreated) {
                    sameSide.merge(faction, 1, Integer::sum);
                    continue;
                }
                Seating treatment = firstTreated ? first : second;
                Seating control = firstTreated ? second : first;
                differences
                        .computeIfAbsent(faction, ignored -> new ArrayList<>())
                        .add((double) (treatment.points().get(faction)
                                - control.points().get(faction)));
                treatmentWins
                        .computeIfAbsent(faction, ignored -> new ArrayList<>())
                        .add(treatment.winShare(faction));
                controlWins
                        .computeIfAbsent(faction, ignored -> new ArrayList<>())
                        .add(control.winShare(faction));
            }
        }
        Set<String> factions = new java.util.TreeSet<>(differences.keySet());
        factions.addAll(sameSide.keySet());
        factions.addAll(unpaired.keySet());
        List<String> lines = new ArrayList<>();
        for (String faction : factions) {
            List<Double> paired = differences.getOrDefault(faction, List.of());
            lines.add(String.format(
                    "A/B faction %s: treatment minus control victory points %s over %d pairs; win share as treatment"
                            + " %.3f vs as control %.3f; same side in both games %d, seated in one game only %d",
                    faction,
                    meanAndError(paired),
                    paired.size(),
                    average(treatmentWins.getOrDefault(faction, List.of())),
                    average(controlWins.getOrDefault(faction, List.of())),
                    sameSide.getOrDefault(faction, 0),
                    unpaired.getOrDefault(faction, 0)));
        }
        return lines;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    static String meanAndError(List<Double> values) {
        return meanAndError(values, 0);
    }

    static boolean completedPair(List<GameResult> pair) {
        return pair.size() == 2 && pair.stream().allMatch(GameResult::completed);
    }

    // t is measured against the null hypothesis: 0 for the point difference, 0.5 for the win share.
    static String meanAndError(List<Double> values, double nullValue) {
        return meanAndError(Stat.of(values), nullValue);
    }

    static String meanAndError(Stat stat, double nullValue) {
        if (stat.n() == 0) return "n/a";
        if (stat.n() == 1) return String.format("%.3f", stat.mean());
        double error = stat.error();
        return String.format(
                "%.3f ± %.3f (t %.2f)", stat.mean(), error, error == 0 ? 0 : (stat.mean() - nullValue) / error);
    }
}
