package ti4.game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    private final List<ActionCardPlay> actionCardPlays = new ArrayList<>();

    public void recordAcPlay(String acName, Player player) {
        actionCardPlays.add(new ActionCardPlay(acName, getTrackedPlayerId(player), null));
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

    private Map<String, AcPlayStats> getAcPlayStats() {
        Map<String, AcPlayStats> acPlayStats = new HashMap<>();
        for (ActionCardPlay actionCardPlay : actionCardPlays) {
            acPlayStats
                    .computeIfAbsent(actionCardPlay.getActionCard(), _ -> new AcPlayStats())
                    .recordPlayWithTarget(actionCardPlay.getTarget());
        }
        return acPlayStats;
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
    private static class AcPlayStats {
        private int totalPlays;
        private final Map<String, Integer> countPerTarget = new HashMap<>();

        void recordPlayWithTarget(String target) {
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

        // Needed for Jackson
        public ActionCardPlay() {}

        ActionCardPlay(String actionCard, String playerId, String target) {
            this.actionCard = actionCard;
            this.playerId = playerId;
            this.target = target;
        }
    }
}
