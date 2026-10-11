package ti4.ai.agenda;

import java.util.function.ToIntFunction;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

// TODO: WIP, needs a redesign. Election odds are guessed from total influence (votes are unknown in the action
// phase), and only three agenda groups from AgendaPolicy are judged; everything else stays on top.
@UtilityClass
public class AgendaPeek {

    public static boolean keepOnTop(Game game, Player seat, String agenda) {
        if (AgendaPolicy.punishesElectedPlayer(agenda) && isAhead(game, seat, Player::getTotalVictoryPoints)) {
            return false;
        }
        if (AgendaPolicy.punishesPlanetOwner(agenda)
                && isAhead(game, seat, player -> player.getPlanets().size())) {
            return false;
        }
        return !AgendaPolicy.favoursElectedPlayer(agenda) || isAhead(game, seat, player -> influence(game, player));
    }

    private static int influence(Game game, Player player) {
        Integer total = Helper.getPlayerInfluenceTotal(player, game);
        return total == null ? 0 : total;
    }

    private static boolean isAhead(Game game, Player seat, ToIntFunction<Player> measure) {
        int own = measure.applyAsInt(seat);
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .allMatch(other -> measure.applyAsInt(other) <= own);
    }
}
