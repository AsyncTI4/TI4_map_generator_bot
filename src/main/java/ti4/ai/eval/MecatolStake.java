package ti4.ai.eval;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MecatolStake {

    static final double IMPERIAL_ROUND_SHARE = 0.25;
    private static final int DEFAULT_GOAL = 10;
    private static final double LEADER_POINTS_PER_ROUND = 2.0;
    private static final int MAX_IMPERIAL_ROUNDS = 2;

    public static double holdValue(Game game, Player player) {
        return ObjectiveValue.VICTORY_POINT_VALUE * IMPERIAL_ROUND_SHARE * imperialRoundsAhead(game);
    }

    private static int imperialRoundsAhead(Game game) {
        int goal = game.getVp() > 0 ? game.getVp() : DEFAULT_GOAL;
        int leader = game.getRealPlayers().stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(0);
        int roundsLeft = (int) Math.ceil(Math.max(0, goal - leader) / LEADER_POINTS_PER_ROUND);
        return Math.clamp(roundsLeft - 1, 0, MAX_IMPERIAL_ROUNDS);
    }
}
