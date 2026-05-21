package ti4.game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameStats {
    public static final String SABOTAGE = "Sabotage";
    public static final String OVERRULE = "Overrule";

    private Map<String, Integer> slashCommandsUsed = new HashMap<>();
    private Map<String, AcPlayStats> acPlayStats = new HashMap<>();

    public void setSlashCommandsUsed(Map<String, Integer> slashCommandsUsed) {
        this.slashCommandsUsed = slashCommandsUsed == null ? new HashMap<>() : slashCommandsUsed;
    }

    public void setAcPlayStats(Map<String, AcPlayStats> acPlayStats) {
        this.acPlayStats = acPlayStats == null ? new HashMap<>() : acPlayStats;
    }

    // Migration: old actionCardsSabotaged JSON field from a previous save format
    @JsonSetter("actionCardsSabotaged")
    public void migrateActionCardsSabotaged(Map<String, Integer> old) {
        if (old == null || old.isEmpty()) {
            return;
        }
        AcPlayStats stats = acPlayStats.computeIfAbsent(SABOTAGE, _ -> new AcPlayStats());
        old.forEach((target, count) -> {
            stats.setTotalPlays(stats.getTotalPlays() + count);
            stats.getCountPerTarget().merge(target, count, Integer::sum);
        });
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

    public void recordAcPlay(String acName) {
        acPlayStats.computeIfAbsent(acName, _ -> new AcPlayStats()).recordPlay();
    }

    public void recordAcPlayWithTarget(String acName, String target) {
        acPlayStats.computeIfAbsent(acName, _ -> new AcPlayStats()).recordPlayWithTarget(target);
    }

    public int getTotalPlays(String acName) {
        AcPlayStats stats = acPlayStats.get(acName);
        return stats == null ? 0 : stats.getTotalPlays();
    }

    public Map<String, Integer> getCountPerTarget(String acName) {
        AcPlayStats stats = acPlayStats.get(acName);
        return stats == null ? Map.of() : stats.getCountPerTarget();
    }

    // Migration from ACS_SABOD legacy text format
    public void setSpecificActionCardSaboCount(String acName, int count) {
        AcPlayStats stats = acPlayStats.computeIfAbsent(SABOTAGE, _ -> new AcPlayStats());
        stats.setTotalPlays(stats.getTotalPlays() + count);
        stats.getCountPerTarget().merge(acName, count, Integer::sum);
    }

    @Getter
    public static class AcPlayStats {
        private int totalPlays;
        private Map<String, Integer> countPerTarget = new HashMap<>();

        public void setTotalPlays(int totalPlays) {
            this.totalPlays = totalPlays;
        }

        public void setCountPerTarget(Map<String, Integer> countPerTarget) {
            this.countPerTarget = countPerTarget == null ? new HashMap<>() : countPerTarget;
        }

        public void recordPlay() {
            totalPlays++;
        }

        public void recordPlayWithTarget(String target) {
            totalPlays++;
            countPerTarget.merge(target, 1, Integer::sum);
        }
    }
}
