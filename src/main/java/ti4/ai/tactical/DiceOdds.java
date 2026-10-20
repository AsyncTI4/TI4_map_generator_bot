package ti4.ai.tactical;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
class DiceOdds {

    static double atLeast(List<Double> hitChances, int needed) {
        double[] hits = {1.0};
        for (double chance : hitChances) {
            double[] next = new double[hits.length + 1];
            for (int count = 0; count < hits.length; count++) {
                next[count] += hits[count] * (1 - chance);
                next[count + 1] += hits[count] * chance;
            }
            hits = next;
        }
        double enough = 0;
        for (int count = Math.max(0, needed); count < hits.length; count++) enough += hits[count];
        return enough;
    }
}
