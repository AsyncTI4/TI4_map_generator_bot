package ti4.service.actioncard;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class CorruptionTimingService {

    private static final String CORRUPTION_ID = "corruption";
    private static final String DRAWN_ROUND_KEY_PREFIX = "corruptionDrawnInStatusRound";

    public static boolean isHeld(Player player) {
        return player.getActionCards().containsKey(CORRUPTION_ID);
    }

    public static void markIfDrawnSince(Game game, Player player, boolean heldBeforeDraw) {
        if (!heldBeforeDraw && isHeld(player)) {
            game.setStoredValue(drawnRoundKey(player), String.valueOf(game.getRound()));
        }
    }

    public static boolean isBlocked(Game game, Player player, String actionCardId) {
        return CORRUPTION_ID.equals(actionCardId)
                && game.getPhaseOfGame().contains("status")
                && String.valueOf(game.getRound()).equals(game.getStoredValue(drawnRoundKey(player)));
    }

    public static String blockedMessage(Player player) {
        return player.getRepresentationUnfogged() + ", you drew _Corruption_ during this status phase,"
                + " after the public objective was revealed, so you cannot play it on that reveal."
                + " Hold it for the next public objective that is revealed.";
    }

    private static String drawnRoundKey(Player player) {
        return DRAWN_ROUND_KEY_PREFIX + player.getFaction();
    }
}
