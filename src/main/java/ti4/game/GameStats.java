package ti4.game;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class GameStats {
    public static final String OVERRULE_STATS_KEY_PREFIX = "overrule_stats_";
    public static final String OVERRULE_STATS_KEY_SEPARATOR = "|";

    private Map<String, Integer> slashCommandsUsed = new HashMap<>();
    private Map<String, Integer> actionCardsSabotaged = new HashMap<>();
    private Map<String, Map<Integer, Integer>> overruleCounts = new HashMap<>();

    public void setSlashCommandsUsed(Map<String, Integer> slashCommandsUsed) {
        this.slashCommandsUsed = slashCommandsUsed == null ? new HashMap<>() : slashCommandsUsed;
    }

    public void setActionCardsSabotaged(Map<String, Integer> actionCardsSabotaged) {
        this.actionCardsSabotaged = actionCardsSabotaged == null ? new HashMap<>() : actionCardsSabotaged;
    }

    public void setOverruleCounts(Map<String, Map<Integer, Integer>> overruleCounts) {
        this.overruleCounts = overruleCounts == null ? new HashMap<>() : overruleCounts;
    }

    public int getSlashCommandsRunCount() {
        return slashCommandsUsed.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void incrementSpecificSlashCommandCount(String fullCommandName) {
        slashCommandsUsed.merge(fullCommandName, 1, Integer::sum);
    }

    public void setSpecificSlashCommandCount(String command, int count) {
        slashCommandsUsed.put(command, count);
    }

    public void setSpecificActionCardSaboCount(String actionCardName, int count) {
        actionCardsSabotaged.put(actionCardName, count);
    }

    public void incrementOverruleCount(String faction, int strategyCard) {
        if (StringUtils.isBlank(faction)) {
            return;
        }
        overruleCounts.computeIfAbsent(faction, _ -> new HashMap<>()).merge(strategyCard, 1, Integer::sum);
    }

    public void setOverruleCount(String faction, int strategyCard, int count) {
        if (StringUtils.isBlank(faction) || count <= 0) {
            return;
        }
        overruleCounts.computeIfAbsent(faction, _ -> new HashMap<>()).put(strategyCard, count);
    }

    public Map<String, Integer> getFlattenedOverruleCounts() {
        Map<String, Integer> flattened = new LinkedHashMap<>();
        overruleCounts.forEach((faction, countsByStrategyCard) -> {
            if (StringUtils.isBlank(faction) || countsByStrategyCard == null) {
                return;
            }
            countsByStrategyCard.forEach((strategyCard, count) -> {
                if (strategyCard != null && count != null && count > 0) {
                    flattened.put(formatOverruleKey(faction, strategyCard), count);
                }
            });
        });
        return flattened;
    }

    public static String formatOverruleKey(String faction, int strategyCard) {
        return faction + OVERRULE_STATS_KEY_SEPARATOR + strategyCard;
    }
}
