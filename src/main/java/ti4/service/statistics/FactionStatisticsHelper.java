package ti4.service.statistics;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FactionStatisticsHelper {

    private static final String OBSIDIAN_ALIAS = "obsidian";
    private static final String FIRMAMENT_ALIAS = "firmament";
    private static final String COMBINED_LABEL = "Obsidian + Firmament";

    private static boolean isObsidianOrFirmament(String faction) {
        return OBSIDIAN_ALIAS.equals(faction) || FIRMAMENT_ALIAS.equals(faction);
    }

    public static void incrementFactionsIntValue(Map<String, Integer> map, String faction) {
        incrementFactionsIntValue(map, faction, 1);
    }

    public static void incrementFactionsIntValue(Map<String, Integer> map, String faction, int increment) {
        map.merge(faction, increment, Integer::sum);
        if (isObsidianOrFirmament(faction)) {
            map.merge(COMBINED_LABEL, increment, Integer::sum);
        }
    }

    public static void incrementFactionsDoubleValue(Map<String, Double> map, String faction) {
        incrementFactionsDoubleValue(map, faction, 1);
    }

    public static void incrementFactionsDoubleValue(Map<String, Double> map, String faction, double increment) {
        map.merge(faction, increment, Double::sum);
        if (isObsidianOrFirmament(faction)) {
            map.merge(COMBINED_LABEL, increment, Double::sum);
        }
    }
}
