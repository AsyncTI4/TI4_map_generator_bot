package ti4.helpers;

import lombok.Data;
import java.util.List;

@Data
public class PdsCoverage {
    private final int count;
    private final float expected;
    private final List<Integer> diceValues;
    private final boolean hasRerolls;

    public PdsCoverage(int count, float expected, List<Integer> diceValues, boolean hasRerolls) {
        this.count = count;
        this.expected = expected;
        this.diceValues = diceValues;
        this.hasRerolls = hasRerolls;
    }
}