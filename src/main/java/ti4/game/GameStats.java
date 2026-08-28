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

    /**
     * Indices of cancels that never happened, newest first so callers can remove them safely.
     *
     * <p>Between 2026-05-21 and 2026-07-19 the Sabotage button recorded its cancel before checking
     * whether the presser held a Sabotage at all, so a stray press wrote a cancel out of thin air.
     * Where that cancel named a card whose only play was already canceled, {@link
     * #migrateTargetsToCanceledFlags()} had nothing left to mark and stood in a player-less
     * placeholder - inserted at the index of the Sabotage that caused it, so the two sit adjacent
     * and come out together.
     *
     * <p>Overrule placeholders are left alone whatever they look like. Legacy recorded no play for
     * that card, so a placeholder is the only trace of a real play, and a game can hold more than
     * one - which is indistinguishable from a stray press once the plays are all that is left.
     *
     * @deprecated one-off cleanup. Remove once it has run against all games.
     */
    @Deprecated
    public List<Integer> findFabricatedCancels() {
        List<Integer> indices = new ArrayList<>();
        for (int i = actionCardPlays.size() - 1; i >= 0; i--) {
            if (!isFabricatedCancel(i)) {
                continue;
            }
            int pairedSabotage = i + 1;
            if (pairedSabotage < actionCardPlays.size()
                    && SABOTAGE.equals(actionCardPlays.get(pairedSabotage).getActionCard())
                    // A player-less "Sabotage" here is a placeholder of its own, not the cause.
                    && actionCardPlays.get(pairedSabotage).getPlayerId() != null) {
                indices.add(pairedSabotage);
            }
            indices.add(i);
        }
        return indices;
    }

    private boolean isFabricatedCancel(int index) {
        ActionCardPlay play = actionCardPlays.get(index);
        if (play.getPlayerId() != null || !play.isCanceled()) {
            return false;
        }
        // Legacy code recorded no play for Overrule, so a player-less cancel of it is the only trace
        // a real play left behind. A second one in the same game can be genuine too - the deck's one
        // copy is reshuffled and replayed in roughly a seventh of games - and nothing in the record
        // separates that from a stray press, so no Overrule placeholder is ever removed.
        if (OVERRULE.equals(play.getActionCard())) {
            return false;
        }

        for (int i = 0; i < index; i++) {
            ActionCardPlay earlier = actionCardPlays.get(i);
            // A play still standing is one this cancel could have belonged to, so it is not ours to
            // judge - the migration would have marked it rather than standing in a placeholder.
            if (play.getActionCard().equals(earlier.getActionCard()) && !earlier.isCanceled()) {
                return false;
            }
        }
        // Every other card's play was recorded as it happened, so a placeholder means there was no
        // play left to cancel when the button was pressed.
        return true;
    }

    /**
     * @deprecated one-off cleanup. Remove along with {@link #findFabricatedCancels()}.
     */
    @Deprecated
    public int removeFabricatedCancels() {
        List<Integer> indices = findFabricatedCancels();
        // Descending, so each removal leaves the indices still to be removed where they were.
        indices.forEach(index -> actionCardPlays.remove((int) index));
        return indices.size();
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
