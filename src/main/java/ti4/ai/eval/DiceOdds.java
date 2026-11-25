package ti4.ai.eval;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DiceOdds {

    private static final double NEGLIGIBLE = 1e-4;

    public static double atLeast(List<Double> hitChances, int needed) {
        double[] hits = distribution(hitChances);
        double enough = 0;
        for (int count = Math.max(0, needed); count < hits.length; count++) enough += hits[count];
        return enough;
    }

    public static double[] distribution(List<Double> hitChances) {
        double[] hits = {1.0};
        for (double chance : hitChances) {
            double[] next = new double[hits.length + 1];
            for (int count = 0; count < hits.length; count++) {
                next[count] += hits[count] * (1 - chance);
                next[count + 1] += hits[count] * chance;
            }
            hits = next;
        }
        return hits;
    }

    public static double[] capped(double[] hits, int cap) {
        int top = Math.max(0, cap);
        if (hits.length <= top + 1) return hits;
        double[] capped = new double[top + 1];
        for (int count = 0; count < hits.length; count++) capped[Math.min(count, top)] += hits[count];
        return capped;
    }

    public static double[] pruned(double[] hits) {
        double kept = 0;
        for (double chance : hits) if (chance >= NEGLIGIBLE) kept += chance;
        if (kept <= 0) return hits;
        double[] pruned = new double[hits.length];
        for (int count = 0; count < hits.length; count++) {
            if (hits[count] >= NEGLIGIBLE) pruned[count] = hits[count] / kept;
        }
        return pruned;
    }

    public static double[] multiplied(double[] hits, int multiplier) {
        if (multiplier <= 1) return hits;
        double[] scaled = new double[(hits.length - 1) * multiplier + 1];
        for (int count = 0; count < hits.length; count++) scaled[count * multiplier] = hits[count];
        return scaled;
    }
}
