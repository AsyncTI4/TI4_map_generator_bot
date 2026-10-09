package ti4.ai.secrets;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
public class SpyNetwork {

    public static final String ID = "fsn";
    public static final int CARDS = 5;

    public static boolean holds(Player seat) {
        return seat.getSecretsUnscored().containsKey(ID);
    }

    public static boolean needsCards(Game game, Player seat) {
        return holds(seat) && ButtonHelper.getACLimit(game, seat) >= CARDS && seat.getAcCount() < CARDS;
    }

    public static boolean withinReach(Game game, Player seat, int draw) {
        return needsCards(game, seat) && seat.getAcCount() + draw >= CARDS;
    }
}
