package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import ti4.ai.AiExperiments;

// The arithmetic of a continuation A/B (ContinuationAbExperimentTest), kept apart so it can be unit tested.
//
// From one snapshot, one continuation seed plays two arms: arm A switches the experiments on for a random half of the
// seats, arm B for the other half. Each arm scores the treated seats' average victory point gain over the
// continuation minus the control seats'. The pair's value is the mean of the two arms, so seat, faction and position
// cancel within the pair. With common random numbers (both arms on the same seed) a pair where the experiments never
// changed a decision plays the same game twice and is worth exactly 0; it is reported as untouched.
final class ContinuationAb {

    private ContinuationAb() {}

    record Arm(Set<String> treated, Map<String, Integer> gains) {

        double difference() {
            return ContinuationAb.difference(gains, treated);
        }
    }

    record Pair(long seed, int round, int continuation, Arm a, Arm b) {

        double value() {
            return (a.difference() + b.difference()) / 2;
        }

        boolean untouched() {
            return a.gains().equals(b.gains());
        }
    }

    // A random half of the seats (rounded down), in seat order.
    static List<Integer> treatedHalf(int seats, Random random) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < seats; index++) order.add(index);
        Collections.shuffle(order, random);
        List<Integer> half = new ArrayList<>(order.subList(0, seats / 2));
        Collections.sort(half);
        return half;
    }

    static double difference(Map<String, Integer> gains, Set<String> treated) {
        double treatedSum = 0;
        double controlSum = 0;
        int treatedSeats = 0;
        int controlSeats = 0;
        for (Map.Entry<String, Integer> seat : gains.entrySet()) {
            if (treated.contains(seat.getKey())) {
                treatedSum += seat.getValue();
                treatedSeats++;
            } else {
                controlSum += seat.getValue();
                controlSeats++;
            }
        }
        if (treatedSeats == 0 || controlSeats == 0) return 0;
        return treatedSum / treatedSeats - controlSum / controlSeats;
    }

    static Map<String, Integer> gains(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> gains = new LinkedHashMap<>();
        after.forEach((seat, points) -> gains.put(seat, points - before.getOrDefault(seat, 0)));
        return gains;
    }

    // "none" or blank is the A/A control; otherwise a comma list of AiExperiments.Experiment names, or "all".
    static Set<AiExperiments.Experiment> parseFlags(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value.trim()))
            return EnumSet.noneOf(AiExperiments.Experiment.class);
        if ("all".equalsIgnoreCase(value.trim())) return EnumSet.allOf(AiExperiments.Experiment.class);
        Set<AiExperiments.Experiment> flags = EnumSet.noneOf(AiExperiments.Experiment.class);
        for (String name : value.split(","))
            if (!name.isBlank())
                flags.add(AiExperiments.Experiment.valueOf(name.trim().toUpperCase()));
        return flags;
    }

    record Estimate(
            int pairs,
            int snapshots,
            int games,
            long untouched,
            AbSummary.Stat overSnapshots,
            AbSummary.Stat overGames) {}

    // Each snapshot's continuations are averaged first, since they share a position. The snapshots of one game are
    // correlated too, so the error is also given over games (each game's snapshots averaged), which is the safer one.
    static Estimate estimate(List<Pair> pairs) {
        Map<String, List<Double>> bySnapshot = new TreeMap<>();
        Map<Long, List<Double>> byGame = new TreeMap<>();
        for (Pair pair : pairs) {
            bySnapshot
                    .computeIfAbsent(pair.seed() + "/" + pair.round(), key -> new ArrayList<>())
                    .add(pair.value());
        }
        Map<String, Double> snapshotMeans = new TreeMap<>();
        bySnapshot.forEach((key, values) -> snapshotMeans.put(key, mean(values)));
        snapshotMeans.forEach((key, value) -> byGame.computeIfAbsent(
                        Long.parseLong(key.substring(0, key.indexOf('/'))), seed -> new ArrayList<>())
                .add(value));
        List<Double> gameMeans =
                byGame.values().stream().map(ContinuationAb::mean).toList();
        long untouched = pairs.stream().filter(Pair::untouched).count();
        return new Estimate(
                pairs.size(),
                snapshotMeans.size(),
                gameMeans.size(),
                untouched,
                AbSummary.Stat.of(new ArrayList<>(snapshotMeans.values())),
                AbSummary.Stat.of(gameMeans));
    }

    static List<String> summary(List<Pair> pairs) {
        Estimate estimate = estimate(pairs);
        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                "pairs %d, snapshots %d, games %d; untouched pairs %d (%.0f%%)",
                estimate.pairs(),
                estimate.snapshots(),
                estimate.games(),
                estimate.untouched(),
                estimate.pairs() == 0 ? 0 : 100.0 * estimate.untouched() / estimate.pairs()));
        lines.add("treatment minus control VP gain, over snapshots: "
                + AbSummary.meanAndError(estimate.overSnapshots(), 0));
        lines.add("treatment minus control VP gain, over games: " + AbSummary.meanAndError(estimate.overGames(), 0));
        return lines;
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
