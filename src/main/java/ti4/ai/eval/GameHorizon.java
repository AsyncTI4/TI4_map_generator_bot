package ti4.ai.eval;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class GameHorizon {

    private static final int DEFAULT_VICTORY_POINTS = 10;
    private static final double LEADER_POINTS_PER_ROUND = 2.0;
    private static final int END_GAP = 4;

    public static int goal(Game game) {
        return game.getVp() > 0 ? game.getVp() : DEFAULT_VICTORY_POINTS;
    }

    public static int leaderPoints(Game game) {
        return game.getRealPlayers().stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(0);
    }

    public static int highestOtherPoints(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(0);
    }

    public static int lateRound(Game game) {
        return (int) Math.ceil(goal(game) / 2.0);
    }

    public static boolean endInSight(Game game) {
        return game.getRound() >= lateRound(game) || leaderPoints(game) >= goal(game) - END_GAP;
    }

    public static int roundsBeforeEnd(Game game) {
        int pointsToEndInSight = goal(game) - END_GAP - leaderPoints(game);
        int untilPointsDo = (int) Math.ceil(pointsToEndInSight / LEADER_POINTS_PER_ROUND);
        return Math.max(0, Math.min(lateRound(game) - game.getRound(), untilPointsDo));
    }

    public static int roundsLeft(Game game) {
        double pointsToGo = Math.max(0, goal(game) - leaderPoints(game));
        return Math.max(1, (int) Math.ceil(pointsToGo / LEADER_POINTS_PER_ROUND));
    }
}
