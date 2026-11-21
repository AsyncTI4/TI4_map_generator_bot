package ti4.ai.tactical;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.ai.promissory.PlayAreaNotes;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.secrets.ActionSecretOdds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class TradedSecretPoint {

    static double share(Game game, Player seat, Tile tile, String secret, double share) {
        if (!PlayAreaNotes.returnsAnyOnActivating(game, seat, tile)) return share;
        if (pointsAfterReturn(game, seat, tile) + 1 >= GameHorizon.goal(game)) {
            return ScoringStakes.WIN_VALUE / ObjectiveValue.VICTORY_POINT_VALUE;
        }
        double scoredNow =
                ScoringStakes.vpWorth(game, seat) * (1 - ActionSecretOdds.afterThisRound(game, seat, secret));
        return Math.min(share, scoredNow / ObjectiveValue.VICTORY_POINT_VALUE);
    }

    static boolean winsOnlyTogether(Game game, Player seat, Tile tile, int secrets) {
        if (secrets < 2 || !PlayAreaNotes.returnsAnyOnActivating(game, seat, tile)) return false;
        int afterReturn = pointsAfterReturn(game, seat, tile);
        int goal = GameHorizon.goal(game);
        return afterReturn + 1 < goal && afterReturn + secrets >= goal;
    }

    static boolean gainsPoints(Game game, Player seat, Tile tile, int secrets) {
        if (!PlayAreaNotes.returnsAnyOnActivating(game, seat, tile)) return true;
        return secrets > PlayAreaNotes.pointsReturnedOnActivating(game, seat, tile);
    }

    private static int pointsAfterReturn(Game game, Player seat, Tile tile) {
        return seat.getTotalVictoryPoints() - PlayAreaNotes.pointsReturnedOnActivating(game, seat, tile);
    }
}
