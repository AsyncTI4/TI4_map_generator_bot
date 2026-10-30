package ti4.ai.scoring;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ThronePoint {

    static final double DECIDES = 1.0;
    static final double WINS_ANYWAY = 0.5;
    static final double RACE = 0.5;
    static final double LOST_CAUSE = 0.15;
    static final double FLOOR = 0.1;
    static final int OUT_OF_RACE_GAP = 5;

    public static double kept(Game game, Player holder, Player giver) {
        return worth(game, holder, giver, holder.getTotalVictoryPoints() - 1);
    }

    public static double gained(Game game, Player holder, Player giver) {
        int points = holder.getTotalVictoryPoints();
        if (points + 1 >= GameHorizon.goal(game)) return ScoringStakes.WIN_VALUE;
        return worth(game, holder, giver, points);
    }

    private static double worth(Game game, Player holder, Player giver, int pointsWithout) {
        return ScoringStakes.vpWorth(game, holder) * share(game, holder, giver, pointsWithout);
    }

    static double share(Game game, Player holder, Player giver, int pointsWithout) {
        int goal = GameHorizon.goal(game);
        int withPoint = pointsWithout + 1;
        double reach = ScoringStakes.pointsInReach(game, holder, holder);
        if (goal - withPoint <= ScoringStakes.CONTENDER_FROM_GOAL || withPoint + reach >= goal) {
            return pointsWithout + reach >= goal ? WINS_ANYWAY : DECIDES;
        }
        if (giver != null && ScoringStakes.contender(game, holder, giver)) return LOST_CAUSE;
        int behind = Math.max(0, GameHorizon.highestOtherPoints(game, holder) - withPoint);
        return Math.max(FLOOR, RACE * (1 - Math.min(1.0, (double) behind / OUT_OF_RACE_GAP)));
    }
}
