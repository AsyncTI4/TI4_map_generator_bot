package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.Threats;
import ti4.ai.scoring.ObjectiveOdds;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class ImperialDenial {

    private static final int MECATOL_POINT = 1;

    static double rivalStake(Game game, Player viewer, Player rival) {
        int mecatol = rival.controlsMecatol(true) ? MECATOL_POINT : 0;
        if (winsBeforeAnyoneCanStopIt(game, rival, mecatol)) return ScoringStakes.WIN_VALUE;
        ObjectiveOdds.ImperialOdds odds =
                ObjectiveOdds.imperial(game, rival, mecatol, ImperialUses.secretChance(game, viewer, rival));
        double distance = StrategyCardRanking.closeToWinning(game, rival) ? 1 : CardValueScale.DISTANT_DENIAL_SHARE;
        double points =
                CardValueScale.DENIAL_SHARE * CardValueScale.VICTORY_POINT * (mecatol + odds.extraPoints()) * distance;
        double win = CardValueScale.DENIAL_SHARE * ScoringStakes.WIN_VALUE * odds.winSwing();
        return Math.max(points, win);
    }

    static boolean winsBeforeAnyoneCanStopIt(Game game, Player rival, int mecatol) {
        int goal = game.getVp();
        if (goal <= 0) return false;
        int secured = securedMecatol(game, rival, mecatol) + bestPublicMetNow(game, rival);
        return secured > 0 && rival.getTotalVictoryPoints() + secured >= goal;
    }

    private static int securedMecatol(Game game, Player rival, int mecatol) {
        Tile tile = game.getMecatolTile();
        if (mecatol == 0 || tile == null) return 0;
        return Threats.reachableByEnemies(game, rival).contains(tile.getPosition()) ? 0 : mecatol;
    }

    private static int bestPublicMetNow(Game game, Player rival) {
        return ObjectivePolicy.scorablePublics(game, rival).stream()
                .mapToInt(ObjectivePolicy::victoryPoints)
                .max()
                .orElse(0);
    }
}
