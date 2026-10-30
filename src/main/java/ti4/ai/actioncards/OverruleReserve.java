package ti4.ai.actioncards;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class OverruleReserve {

    static final double LATE_POINTS = 0.9;
    static final double LATE_VP_WORTH = 12;
    static final double VALUE = LATE_POINTS * LATE_VP_WORTH;
    private static final int OVERRULES_OWN_POINT = 1;

    static boolean active(Game game, Player seat, PlayMoment moment) {
        if (!ScoringStakes.contenderExists(game)) return true;
        if (moment == PlayMoment.BEFORE_PASSING) return false;
        return canWinWithTheCard(game, seat);
    }

    static double keepFloor(Game game, Player seat, String alias) {
        return active(game, seat, PlayMoment.IN_TURN) ? VALUE : ActionCardPrior.keepPrior(alias);
    }

    private static boolean canWinWithTheCard(Game game, Player seat) {
        double reach = seat.getTotalVictoryPoints() + ScoringStakes.pointsInReach(game, seat, seat);
        return ScoringStakes.contender(game, seat, seat) || reach + OVERRULES_OWN_POINT >= game.getVp();
    }
}
