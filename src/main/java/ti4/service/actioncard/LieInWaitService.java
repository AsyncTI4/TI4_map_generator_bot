package ti4.service.actioncard;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class LieInWaitService {

    private static final String LIE_IN_WAIT_ID = "lieinwait";
    static final String WINDOW_KEY = "lieInWaitWindow";
    static final String REMINDED_KEY = "lieInWaitReminded";

    public static void check(Player p1, Player p2, Game game) {
        if (p1 == null || p2 == null || p1 == p2) {
            return;
        }
        for (Player p3 : game.getRealPlayers()) {
            if (p3 == p1 || p3 == p2 || !p3.getPlayableActionCards().contains(LIE_IN_WAIT_ID)) {
                continue;
            }
            Set<Player> neighbours = p3.getAbilityNeighbours();
            if (!neighbours.contains(p1) || !neighbours.contains(p2)) {
                continue;
            }
            if (!recordReminder(game, p3, p1, p2)) {
                continue;
            }
            MessageHelper.sendMessageToChannel(
                    p3.getCardsInfoThread(),
                    p3.getRepresentation()
                            + " this is a reminder that you have _Lie In Wait_, and two of your neighbors just resolved a transaction. The two neighbors are "
                            + FoWHelper.identityOrColorIfCanSeeStats(game, p1, p3, p1.getRepresentationNoPing())
                            + " and "
                            + FoWHelper.identityOrColorIfCanSeeStats(game, p2, p3, p2.getRepresentationNoPing())
                            + ".");
        }
    }

    static boolean recordReminder(Game game, Player holder, Player p1, Player p2) {
        String window = window(game);
        if (!window.equals(game.getStoredValue(WINDOW_KEY))) {
            game.setStoredValue(WINDOW_KEY, window);
            game.removeStoredValue(REMINDED_KEY);
        }
        String reminder = reminderKey(holder, p1, p2);
        Set<String> reminded = new HashSet<>();
        String stored = game.getStoredValue(REMINDED_KEY);
        if (!stored.isEmpty()) {
            reminded.addAll(Arrays.asList(stored.split(",")));
        }
        if (!reminded.add(reminder)) {
            return false;
        }
        game.setStoredValue(REMINDED_KEY, String.join(",", reminded));
        return true;
    }

    private static String window(Game game) {
        return game.getRound() + "|" + game.getPhaseOfGame() + "|" + game.getActivePlayerID() + "|"
                + activePlayerTurn(game) + "|" + game.getCurrentAgendaInfo();
    }

    private static int activePlayerTurn(Game game) {
        Player activePlayer = game.getActivePlayer();
        return activePlayer == null ? 0 : activePlayer.getInRoundTurnCount();
    }

    private static String reminderKey(Player holder, Player p1, Player p2) {
        String first = p1.getFaction();
        String second = p2.getFaction();
        if (first.compareTo(second) > 0) {
            String swap = first;
            first = second;
            second = swap;
        }
        return holder.getFaction() + ":" + first + "+" + second;
    }
}
