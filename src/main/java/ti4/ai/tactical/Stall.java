package ti4.ai.tactical;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class Stall {

    public static final double ACTION_VALUE = 1.5;

    public static double actionValue(Game game, Player seat) {
        return othersStillActing(game, seat) ? ACTION_VALUE : 0;
    }

    public static boolean othersStillActing(Game game, Player seat) {
        return game.getRealPlayers().stream().anyMatch(other -> other != seat && !other.isPassed());
    }
}
