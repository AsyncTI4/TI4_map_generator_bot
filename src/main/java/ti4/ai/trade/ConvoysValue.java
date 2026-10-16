package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class ConvoysValue {

    static final double GIVE_COST = 0.5;
    static final double OWN_BACK = 0.5;
    private static final double PER_NEW_PARTNER = 0.5;
    private static final double CAP = 2.0;

    static boolean isConvoys(String alias) {
        return TradeLegality.CONVOYS.contains(alias);
    }

    static double received(Game game, Player receiver) {
        if (AiSeats.isAiSeat(receiver)) return NotesForTrade.OTHER_NOTE;
        return Math.min(CAP, PER_NEW_PARTNER * newPartners(game, receiver));
    }

    static long newPartners(Game game, Player receiver) {
        return Standings.others(game, receiver).stream()
                .filter(other -> !TradeLegality.canTransactInActionPhase(game, receiver, other))
                .count();
    }
}
