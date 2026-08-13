package ti4.game;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
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

    // Cancels are attributed to the newest play of that card which is not already canceled, so a
    // player who has a copy canceled and then plays another copy only has the first one flagged.
    public boolean markLatestPlayCanceled(String acName) {
        for (int i = actionCardPlays.size() - 1; i >= 0; i--) {
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

    private static String getTrackedPlayerId(Player player) {
        if (player == null) {
            return null;
        }
        return StringUtils.defaultIfBlank(player.getStatsTrackedUserID(), player.getUserID());
    }

    @EqualsAndHashCode
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActionCardPlay {
        private String actionCard;
        private String playerId;

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
    }
}
