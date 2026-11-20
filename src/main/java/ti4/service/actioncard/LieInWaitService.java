package ti4.service.actioncard;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class LieInWaitService {

    private static final String LIE_IN_WAIT_ID = "lieinwait";

    public static void check(Player p1, Player p2, Game game) {
        for (Player p3 : game.getRealPlayers()) {
            if (p3 == p1 || p3 == p2 || !p3.getPlayableActionCards().contains(LIE_IN_WAIT_ID)) {
                continue;
            }
            Set<Player> neighbours = p3.getAbilityNeighbours();
            if (!neighbours.contains(p1) || !neighbours.contains(p2)) {
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
}
