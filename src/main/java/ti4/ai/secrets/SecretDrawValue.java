package ti4.ai.secrets;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SecretDrawValue {

    public static final double SWAP_VALUE = 0.3;
    static final double ROUND_SHARE = 0.25;
    private static final int DEFAULT_GOAL = 10;
    private static final double LEADER_POINTS_PER_ROUND = 2.0;

    public static boolean hasRoom(Player seat) {
        return seat.getSecretsUnscored().size() < Math.max(0, seat.getMaxSOCount() - seat.getSoScored());
    }

    public static double of(Game game, Player seat) {
        if (hasRoom(seat)) return pointValue(game, seat, expectedPoints(game, seat));
        if (!SecretDraws.weakHand(game, seat)) {
            return Math.min(pointValue(game, seat, expectedPoints(game, seat)), SWAP_VALUE);
        }
        double gain = Math.max(0, swapPoints(game, seat) - SecretDraws.weakestKeep(game, seat));
        return Math.max(SWAP_VALUE, pointValue(game, seat, gain));
    }

    static double pointValue(Game game, Player seat, double points) {
        return points * ScoringStakes.scoreValue(game, seat, 1);
    }

    static double expectedPoints(Game game, Player seat) {
        return expectedPoints(game, seat, seat.getSecretsUnscored().size());
    }

    static double swapPoints(Game game, Player seat) {
        return expectedPoints(game, seat, Math.max(0, seat.getSecretsUnscored().size() - 1));
    }

    private static double expectedPoints(Game game, Player seat, int competing) {
        int roundsLeft = roundsLeft(game);
        double readiness = unseenReadiness(game, seat);
        double eventually = readiness + (1 - readiness) * Math.min(1.0, roundsLeft * ROUND_SHARE);
        double scoringTurns = Math.min(1.0, Math.max(0, roundsLeft - competing));
        return eventually * scoringTurns;
    }

    static double unseenReadiness(Game game, Player seat) {
        List<String> pool = SecretOdds.unseenPool(game, seat);
        if (pool.isEmpty()) return 0;
        ObjectiveValue board = ObjectiveValue.publicOnly(game, seat);
        return pool.stream()
                .mapToDouble(id -> SecretOdds.readiness(game, board, id))
                .average()
                .orElse(0);
    }

    static int roundsLeft(Game game) {
        int goal = game.getVp() > 0 ? game.getVp() : DEFAULT_GOAL;
        int leader = game.getRealPlayers().stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(0);
        return Math.max(1, (int) Math.ceil(Math.max(0, goal - leader) / LEADER_POINTS_PER_ROUND));
    }
}
