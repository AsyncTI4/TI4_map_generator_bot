package ti4.ai.actioncards;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
class HandPressure {

    static final double NEAR_LIMIT_SHARE = 0.5;
    static final double ENDGAME_SHARE = 0.5;
    private static final int ENDGAME_ROUNDS = 1;

    static double share(Game game, Player seat, PlayMoment moment, String alias) {
        if (alias.equals(forcedDiscard(game, seat, moment))) return 0;
        double share = 1.0;
        if (seat.getAcCount() >= ButtonHelper.getACLimit(game, seat) - 1) share = Math.min(share, NEAR_LIMIT_SHARE);
        if (GameHorizon.roundsLeft(game) <= ENDGAME_ROUNDS) share = Math.min(share, ENDGAME_SHARE);
        return share;
    }

    private static String forcedDiscard(Game game, Player seat, PlayMoment moment) {
        if (moment != PlayMoment.BEFORE_PASSING || seat.getAcCount() < ButtonHelper.getACLimit(game, seat)) return null;
        List<String> order = ActionCardValue.discardOrder(game, seat);
        return order.isEmpty() ? null : order.getFirst();
    }
}
