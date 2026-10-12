package ti4.ai.scoring;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.secrets.SecretOdds;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ScoringStakes {

    public static final double WIN_VALUE = 30;
    public static final int CONTENDER_FROM_GOAL = 2;
    private static final double VP_BASE = 6;
    private static final double VP_CAP = 12;
    private static final double VP_STEP = 2;
    private static final double CONTENDER_BONUS = 2;
    private static final int RISES_FROM_GOAL = 4;
    private static final double RIVAL_POINT_SHARE = 0.25;
    private static final int MECATOL_POINTS = 1;

    public static double vpWorth(Game game, Player player) {
        int beyond = Math.max(0, player.getTotalVictoryPoints() - (game.getVp() - RISES_FROM_GOAL));
        double worth = VP_BASE + VP_STEP * beyond + (contenderExists(game) ? CONTENDER_BONUS : 0);
        return Math.min(VP_CAP, worth);
    }

    public static double scoreValue(Game game, Player player, double points) {
        if (points > 0 && player.getTotalVictoryPoints() + points >= game.getVp()) return WIN_VALUE;
        return points * vpWorth(game, player);
    }

    public static double rivalStake(Game game, Player rival, double points) {
        boolean reachesGoal = points > 0 && rival.getTotalVictoryPoints() + points >= game.getVp();
        return scoreValue(game, rival, points) * (reachesGoal ? 1.0 : RIVAL_POINT_SHARE);
    }

    public static boolean contenderExists(Game game) {
        return game.getRealPlayers().stream()
                .filter(player -> !player.isEliminated())
                .anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp() - CONTENDER_FROM_GOAL);
    }

    public static double pointsInReach(Game game, Player viewer, Player player) {
        double points = publicPoints(game, player);
        if (holdsUnplayedImperial(game, player) && player.controlsMecatol(true)) points += MECATOL_POINTS;
        return points + SecretOdds.chanceReady(game, viewer, player);
    }

    public static boolean contender(Game game, Player viewer, Player player) {
        int goal = game.getVp();
        if (goal <= 0) return false;
        int points = player.getTotalVictoryPoints();
        return goal - points <= CONTENDER_FROM_GOAL || points + pointsInReach(game, viewer, player) >= goal;
    }

    public static boolean anyOpponentIsContender(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && !other.isEliminated())
                .anyMatch(other -> contender(game, seat, other));
    }

    private static int publicPoints(Game game, Player player) {
        List<String> scorable = ObjectivePolicy.scorablePublics(game, player);
        int chances = ScoringReserve.scoringChances(game, player);
        return scorable.stream()
                .map(ObjectivePolicy::victoryPoints)
                .sorted(Comparator.reverseOrder())
                .limit(chances)
                .mapToInt(Integer::intValue)
                .sum();
    }

    public static boolean holdsUnplayedImperial(Game game, Player player) {
        return player.getSCs().stream()
                .anyMatch(card -> StrategyCard.of(game, card) == StrategyCard.IMPERIAL
                        && !game.getPlayedSCs().contains(card));
    }
}
