package ti4.ai.yin;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class YinAscendant {

    static final String BREAKTHROUGH = "yinbt";
    static final double PER_ALLIANCE = 1.5;

    public static double value(Game game, Player seat) {
        if (!seat.getBreakthroughIDs().contains(BREAKTHROUGH) || seat.isBreakthroughUnlocked(BREAKTHROUGH)) return 0;
        return PER_ALLIANCE * (1 + GameHorizon.roundsLeft(game));
    }
}
