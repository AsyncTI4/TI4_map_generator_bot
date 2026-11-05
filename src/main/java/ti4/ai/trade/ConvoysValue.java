package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ConvoysValue {

    static final double WORTH = 1.0;
    static final double GIVE_COST = WORTH;
    static final double OWN_BACK = 0.5;

    public static boolean isConvoys(String alias) {
        return TradeLegality.CONVOYS.contains(alias);
    }

    public static long newPartners(Game game, Player receiver) {
        return Standings.others(game, receiver).stream()
                .filter(other -> !TradeLegality.canTransactInActionPhase(game, receiver, other))
                .count();
    }
}
