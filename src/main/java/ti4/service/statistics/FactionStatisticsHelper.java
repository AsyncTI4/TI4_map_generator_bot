package ti4.service.statistics;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class FactionStatisticsHelper {

    private static final String OBSIDIAN_ALIAS = "obsidian";
    private static final String FIRMAMENT_ALIAS = "firmament";
    private static final String OBSIDIAN_FIRMAMENT_FACTION_LABEL = "Obsidian + Firmament";

    private static boolean isObsidianOrFirmament(String faction) {
        return OBSIDIAN_ALIAS.equals(faction) || FIRMAMENT_ALIAS.equals(faction);
    }

    public static void incrementFactionsIntValue(Map<String, Integer> map, String faction) {
        incrementFactionsIntValue(map, faction, 1);
    }

    public static void incrementFactionsIntValue(Map<String, Integer> map, String faction, int increment) {
        map.merge(faction, increment, Integer::sum);
        if (isObsidianOrFirmament(faction)) {
            map.merge(OBSIDIAN_FIRMAMENT_FACTION_LABEL, increment, Integer::sum);
        }
    }

    public static void incrementFactionsDoubleValue(Map<String, Double> map, String faction) {
        incrementFactionsDoubleValue(map, faction, 1);
    }

    public static void incrementFactionsDoubleValue(Map<String, Double> map, String faction, double increment) {
        map.merge(faction, increment, Double::sum);
        if (isObsidianOrFirmament(faction)) {
            map.merge(OBSIDIAN_FIRMAMENT_FACTION_LABEL, increment, Double::sum);
        }
    }

    public static String getFactionEmoji(String faction) {
        FactionModel factionModel = Mapper.getFaction(faction);
        return factionModel != null
                ? factionModel.getFactionEmoji()
                : OBSIDIAN_FIRMAMENT_FACTION_LABEL.equals(faction)
                        ? FactionEmojis.Firma_Obs.emojiString()
                        : "\uD83D\uDC7B";
    }
}
