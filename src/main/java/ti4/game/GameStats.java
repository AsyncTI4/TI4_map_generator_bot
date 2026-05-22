package ti4.game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameStats {
    public static final String SABOTAGE = "Sabotage";
    public static final String OVERRULE = "Overrule";

    private List<ActionCardPlay> actionCardPlays = new ArrayList<>();

    public void setActionCardPlays(List<ActionCardPlay> actionCardPlays) {
        this.actionCardPlays = actionCardPlays == null ? new ArrayList<>() : actionCardPlays;
    }

    @JsonSetter("acPlayStats")
    public void setAcPlayStats(Map<String, AcPlayStats> acPlayStats) {
        if (!actionCardPlays.isEmpty() || acPlayStats == null || acPlayStats.isEmpty()) {
            return;
        }
        acPlayStats.forEach(this::migrateLegacyAcPlayStats);
    }

    // Migration: old actionCardsSabotaged JSON field from a previous save format
    @JsonSetter("actionCardsSabotaged")
    public void migrateActionCardsSabotaged(Map<String, Integer> old) {
        if (!actionCardPlays.isEmpty() || old == null || old.isEmpty()) {
            return;
        }
        old.forEach((target, count) -> repeatPlayRecording(SABOTAGE, null, target, count));
    }

    public void recordAcPlay(String acName) {
        recordAcPlay(acName, null);
    }

    public void recordAcPlay(String acName, Player player) {
        actionCardPlays.add(new ActionCardPlay(acName, getTrackedPlayerId(player), null));
    }

    public void recordAcPlayWithTarget(String acName, String target) {
        recordAcPlayWithTarget(acName, null, target);
    }

    public void recordAcPlayWithTarget(String acName, Player player, String target) {
        actionCardPlays.add(new ActionCardPlay(acName, getTrackedPlayerId(player), normalizeTarget(target)));
    }

    public int getTotalPlays(String acName) {
        AcPlayStats stats = getAcPlayStats().get(acName);
        return stats == null ? 0 : stats.getTotalPlays();
    }

    public Map<String, Integer> getCountPerTarget(String acName) {
        AcPlayStats stats = getAcPlayStats().get(acName);
        return stats == null ? Map.of() : stats.getCountPerTarget();
    }

    // Migration from ACS_SABOD legacy text format
    public void setSpecificActionCardSaboCount(String acName, int count) {
        repeatPlayRecording(SABOTAGE, null, acName, count);
    }

    public Map<String, AcPlayStats> getAcPlayStats() {
        Map<String, AcPlayStats> acPlayStats = new HashMap<>();
        for (ActionCardPlay actionCardPlay : actionCardPlays) {
            acPlayStats
                    .computeIfAbsent(actionCardPlay.getActionCard(), _ -> new AcPlayStats())
                    .recordPlayWithTarget(actionCardPlay.getTarget());
        }
        return acPlayStats;
    }

    private void migrateLegacyAcPlayStats(String acName, AcPlayStats stats) {
        if (stats == null) {
            return;
        }
        int migratedTargetedPlays = 0;
        for (Map.Entry<String, Integer> entry : stats.getCountPerTarget().entrySet()) {
            repeatPlayRecording(acName, null, entry.getKey(), entry.getValue());
            migratedTargetedPlays += entry.getValue();
        }
        repeatPlayRecording(acName, null, null, Math.max(0, stats.getTotalPlays() - migratedTargetedPlays));
    }

    private void repeatPlayRecording(String acName, String playerId, String target, int count) {
        for (int i = 0; i < count; i++) {
            actionCardPlays.add(new ActionCardPlay(acName, playerId, normalizeTarget(target)));
        }
    }

    private static String getTrackedPlayerId(Player player) {
        if (player == null) {
            return null;
        }
        return StringUtils.defaultIfBlank(player.getStatsTrackedUserID(), player.getUserID());
    }

    private static String normalizeTarget(String target) {
        return StringUtils.isBlank(target) ? null : target;
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
            if (StringUtils.isNotBlank(target)) {
                countPerTarget.merge(target, 1, Integer::sum);
            }
        }
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ActionCardPlay {
        private String actionCard;
        private String playerId;
        private String target;

        public ActionCardPlay() {}

        public ActionCardPlay(String actionCard, String playerId, String target) {
            this.actionCard = actionCard;
            this.playerId = playerId;
            this.target = target;
        }

        public void setActionCard(String actionCard) {
            this.actionCard = actionCard;
        }

        public void setPlayerId(String playerId) {
            this.playerId = playerId;
        }

        public void setTarget(String target) {
            this.target = normalizeTarget(target);
        }
    }
}
