package ti4.ai.tactical;

import lombok.experimental.UtilityClass;
import ti4.ai.endurance.Endurance;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class Stall {

    public static final double ACTION_VALUE = 1.5;

    public static double value(Game game, Player seat) {
        return ACTION_VALUE + Endurance.stallBonus(game, seat);
    }

    public static double actionValue(Game game, Player seat) {
        return othersStillActing(game, seat) ? value(game, seat) : 0;
    }

    public static double cardActionValue(Game game, Player seat) {
        if (!othersStillActing(game, seat) || Endurance.savesCardsForLater(game, seat)) return 0;
        return value(game, seat);
    }

    public static boolean othersStillActing(Game game, Player seat) {
        return game.getRealPlayers().stream().anyMatch(other -> other != seat && !other.isPassed());
    }
}
