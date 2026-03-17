package ti4.service.statistics;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FirmamentObsidianStatisticsHelper {

    public static final String OBSIDIAN_ALIAS = "obsidian";
    public static final String FIRMAMENT_ALIAS = "firmament";
    public static final String COMBINED_LABEL = "Obsidian + Firmament";

    public static int getCombinedCount(Map<String, Integer> values) {
        return values.getOrDefault(OBSIDIAN_ALIAS, 0) + values.getOrDefault(FIRMAMENT_ALIAS, 0);
    }

    public static double getCombinedCount(Map<String, Double> values) {
        return values.getOrDefault(OBSIDIAN_ALIAS, 0.0) + values.getOrDefault(FIRMAMENT_ALIAS, 0.0);
    }
}
