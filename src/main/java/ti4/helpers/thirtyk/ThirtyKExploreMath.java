package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ThirtyKExploreMath {

    public static final double SYMPATHIES_VICTORY_POINT_COST = 4.0;

    public static int countAtLeast(List<Integer> results, int minimum) {
        return (int) results.stream().filter(result -> result >= minimum).count();
    }

    public static int countAtMost(List<Integer> results, int maximum) {
        return (int) results.stream().filter(result -> result <= maximum).count();
    }

    public static boolean sympathiesEarnsVictoryPoint(double destroyedShipCost) {
        return destroyedShipCost >= SYMPATHIES_VICTORY_POINT_COST;
    }

    public static int wastefulPiratesTradeGoods(int hits, int hitCapacity) {
        return Math.max(0, Math.min(hits, hitCapacity));
    }

    public static int crossSpeciesInfantry(int planetsWithForeignInfantry) {
        return planetsWithForeignInfantry + 1;
    }

    public static <T> Optional<T> pickFewestPoints(Map<T, Integer> pointsByCandidate, RandomGenerator random) {
        if (pointsByCandidate.isEmpty()) return Optional.empty();
        int fewest = pointsByCandidate.values().stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);
        List<T> lastPlace = new ArrayList<>();
        pointsByCandidate.forEach((candidate, points) -> {
            if (points == fewest) lastPlace.add(candidate);
        });
        return Optional.of(lastPlace.get(random.nextInt(lastPlace.size())));
    }

    public static String readyCycleMarker(int round, String phase) {
        String lowerPhase = phase == null ? "" : phase.toLowerCase();
        boolean afterStatusReady = "statushomework".equals(lowerPhase) || lowerPhase.contains("agenda");
        return round + (afterStatusReady ? "s" : "");
    }
}
