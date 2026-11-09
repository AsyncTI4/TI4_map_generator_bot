package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
            double treatmentWinShare) {

        double difference() {
            return treatmentPoints - controlPoints;
        }
    }

    static GameResult result(SelfPlayGame.Outcome outcome) {
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
                winners == 0 ? 0.5 : (double) treatmentWinners / winners);
    }

    static void print(List<SelfPlayGame.Outcome> outcomes, Set<AiExperiments.Experiment> experiments) {
        List<GameResult> results = outcomes.stream().map(AbSummary::result).toList();
        for (GameResult game : results) {
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
        Map<Long, List<GameResult>> pairs = new TreeMap<>();
        for (GameResult game : results)
            pairs.computeIfAbsent(game.seed(), seed -> new ArrayList<>()).add(game);
        List<Double> pairDifferences = new ArrayList<>();
        List<Double> pairWinShares = new ArrayList<>();
        for (List<GameResult> pair : pairs.values()) {
            if (pair.size() != 2) continue;
            pairDifferences.add((pair.get(0).difference() + pair.get(1).difference()) / 2);
            pairWinShares.add((pair.get(0).treatmentWinShare() + pair.get(1).treatmentWinShare()) / 2);
        }
        System.out.printf(
                "A/B %s: %d games, %d complete pairs; treatment minus control victory points %s; treatment win share"
                        + " %s (null 0.500); treatment %.2f vs control %.2f average points%n",
                experiments.isEmpty() ? "none (A/A)" : experiments,
                results.size(),
                pairDifferences.size(),
                meanAndError(pairDifferences),
                meanAndError(pairWinShares),
                results.stream()
                        .mapToDouble(GameResult::treatmentPoints)
                        .average()
                        .orElse(0),
                results.stream()
                        .mapToDouble(GameResult::controlPoints)
                        .average()
                        .orElse(0));
    }

    static String meanAndError(List<Double> values) {
        int n = values.size();
        if (n == 0) return "n/a";
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (n == 1) return String.format("%.3f", mean);
        double variance = values.stream()
                        .mapToDouble(value -> (value - mean) * (value - mean))
                        .sum()
                / (n - 1);
        double error = Math.sqrt(variance / n);
        return String.format("%.3f ± %.3f (t %.2f)", mean, error, error == 0 ? 0 : mean / error);
    }
}
