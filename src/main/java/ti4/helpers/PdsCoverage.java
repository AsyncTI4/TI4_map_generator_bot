package ti4.helpers;

import java.util.List;
import lombok.Data;

@Data
public class PdsCoverage {
    private final int count;
    private final float expected;
    private final List<Integer> diceValues;
    private final boolean hasRerolls;

    PdsCoverage(int count, float expected, List<Integer> diceValues, boolean hasRerolls) {
        this.count = count;
        this.expected = expected;
        this.diceValues = diceValues;
        this.hasRerolls = hasRerolls;
    }
}
