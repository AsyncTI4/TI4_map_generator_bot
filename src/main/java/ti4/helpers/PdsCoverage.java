package ti4.helpers;

import java.util.List;
import lombok.Data;

@Data
public record PdsCoverage(int count, float expected, List<Integer> diceValues, boolean hasRerolls) {}
