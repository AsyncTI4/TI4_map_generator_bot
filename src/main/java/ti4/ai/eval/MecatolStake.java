package ti4.ai.eval;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MecatolStake {

    static final double IMPERIAL_ROUND_SHARE = 0.25;
    private static final int MAX_IMPERIAL_ROUNDS = 2;

    public static double holdValue(Game game, Player player) {
        return ObjectiveValue.VICTORY_POINT_VALUE * IMPERIAL_ROUND_SHARE * imperialRoundsAhead(game);
    }

    private static int imperialRoundsAhead(Game game) {
        return Math.clamp(GameHorizon.roundsLeft(game) - 1, 0, MAX_IMPERIAL_ROUNDS);
    }
}
