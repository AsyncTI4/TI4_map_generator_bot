package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.SearchMemo;
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
    private static final String PROJECTED_MEMO = "stakes.projectedPoints";

    public record OverrulePlay(int points, boolean wins) {

        static final OverrulePlay NOTHING = new OverrulePlay(0, false);
    }

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

    public static boolean lateGame(Game game) {
        return game.getVp() > 0
                && game.getRealPlayers().stream()
                        .filter(player -> !player.isEliminated())
                        .anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp() - RISES_FROM_GOAL);
    }

    public static double pointsInReach(Game game, Player viewer, Player player) {
        double points = publicPoints(game, player);
        if (holdsUnplayedImperial(game, player) && player.controlsMecatol(true)) points += MECATOL_POINTS;
        return points + SecretOdds.chanceReady(game, viewer, player);
    }

    public static double tempoLead(Game game, Player viewer, Player player) {
        double others = game.getRealPlayers().stream()
                .filter(other -> other != player && !other.isEliminated())
                .mapToDouble(other -> projectedPoints(game, viewer, other))
                .average()
                .orElse(0);
        return projectedPoints(game, viewer, player) - others;
    }

    private static double projectedPoints(Game game, Player viewer, Player player) {
        return SearchMemo.remember(
                () -> player.getTotalVictoryPoints()
                        + ScoringPaths.readyPoints(game, player)
                        + SecretOdds.chanceReady(game, viewer, player),
                PROJECTED_MEMO,
                viewer,
                player);
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

    public static int overrulePoints(Game game, Player player) {
        return overrulePlay(game, player).points();
    }

    public static OverrulePlay overrulePlay(Game game, Player player) {
        if (!imperialIsUnplayed(game)) return OverrulePlay.NOTHING;
        List<Integer> publics = qualifyingPublicsWithOneSpend(game, player);
        int chances = ScoringReserve.scoringChances(game, player);
        int extraPublic = sum(publics, chances + 1) - sum(publics, chances);
        int mecatol = player.controlsMecatol(true) ? MECATOL_POINTS : 0;
        int points = extraPublic + mecatol;
        int bestPublic = publics.isEmpty() ? 0 : publics.getFirst();
        int immediate = bestPublic + mecatol;
        boolean wins = immediate > 0 && game.getVp() > 0 && player.getTotalVictoryPoints() + immediate >= game.getVp();
        if (!wins) return new OverrulePlay(points, false);
        return new OverrulePlay(Math.max(points, game.getVp() - player.getTotalVictoryPoints()), true);
    }

    private static List<Integer> qualifyingPublicsWithOneSpend(Game game, Player player) {
        List<String> scorable = ObjectivePolicy.scorablePublics(game, player);
        List<Integer> points = new ArrayList<>(scorable.stream()
                .filter(id -> !ObjectiveCatalog.isSpend(id))
                .map(ObjectivePolicy::victoryPoints)
                .toList());
        scorable.stream()
                .filter(ObjectiveCatalog::isSpend)
                .map(ObjectivePolicy::victoryPoints)
                .max(Comparator.naturalOrder())
                .ifPresent(points::add);
        points.sort(Comparator.reverseOrder());
        return points;
    }

    private static int sum(List<Integer> descending, int count) {
        return descending.stream().limit(count).mapToInt(Integer::intValue).sum();
    }

    public static boolean imperialIsUnplayed(Game game) {
        return game.getSCList().stream()
                .anyMatch(card -> StrategyCard.of(game, card) == StrategyCard.IMPERIAL
                        && !game.getPlayedSCs().contains(card));
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
