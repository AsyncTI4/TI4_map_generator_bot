package ti4.game;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameStats {
    public static final String SABOTAGE = "Sabotage";
    public static final String OVERRULE = "Overrule";

    @Getter
    @Setter
    private List<ActionCardPlay> actionCardPlays = new ArrayList<>();

    public void recordAcPlay(String acName, Player player) {
        actionCardPlays.add(new ActionCardPlay(acName, getTrackedPlayerId(player)));
    }

    public boolean markLatestPlayCanceled(String acName) {
        return markLatestPlayCanceledBefore(acName, actionCardPlays.size());
    }

    private boolean markLatestPlayCanceledBefore(String acName, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            ActionCardPlay play = actionCardPlays.get(i);
            if (acName.equals(play.getActionCard()) && !play.isCanceled()) {
                play.setCanceled(true);
                return true;
            }
        }
        return false;
    }

    public int getTotalPlays(String acName) {
        return (int) actionCardPlays.stream()
                .filter(play -> acName.equals(play.getActionCard()))
                .count();
    }

    /**
     * @deprecated one-off migration for saves where cancels were recorded as Sabotage plays
     *     targeting the canceled card, and Overrule plays carried the chosen strategy card. Remove
     *     this, {@link ActionCardPlay#getTarget()} and {@link OverruleTargetMigration} once it has
     *     run against all games.
     */
    @Deprecated
    public OverruleTargetMigration migrateTargetsToCanceledFlags() {
        Map<String, Integer> strategyCardChoices = new HashMap<>();
        boolean changed = false;
        for (int i = 0; i < actionCardPlays.size(); i++) {
            ActionCardPlay play = actionCardPlays.get(i);
            String target = play.getTarget();
            if (target == null) {
                continue;
            }
            if (OVERRULE.equals(play.getActionCard())) {
                strategyCardChoices.merge(target, 1, Integer::sum);
            } else if (SABOTAGE.equals(play.getActionCard()) && !markLatestPlayCanceledBefore(target, i)) {
                // The canceled play was never recorded (e.g. an Overrule canceled before its
                // strategy card was chosen); stand in for it so the cancel still gets counted.
                ActionCardPlay placeholder = new ActionCardPlay(target, null);
                placeholder.setCanceled(true);
                actionCardPlays.add(i, placeholder);
                i++;
            }
            play.setTarget(null);
            changed = true;
        }
        return new OverruleTargetMigration(strategyCardChoices, changed);
    }

    private static String getTrackedPlayerId(Player player) {
        if (player == null) {
            return null;
        }
        return StringUtils.defaultIfBlank(player.getStatsTrackedUserID(), player.getUserID());
    }

    /**
     * @deprecated remove along with {@link #migrateTargetsToCanceledFlags()}.
     */
    @Deprecated
    public record OverruleTargetMigration(Map<String, Integer> strategyCardChoices, boolean changed) {}

    @EqualsAndHashCode
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActionCardPlay {
        private String actionCard;
        private String playerId;

        /**
         * @deprecated only still read by {@link GameStats#migrateTargetsToCanceledFlags()} so that
         *     legacy saves can be converted. Remove once that migration has run against all games.
         */
        @Deprecated
        private String target;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean canceled;

        // Needed for Jackson
        public ActionCardPlay() {}

        ActionCardPlay(String actionCard, String playerId) {
            this.actionCard = actionCard;
            this.playerId = playerId;
        }

        void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }

        void setTarget(String target) {
            this.target = target;
        }
    }
}
