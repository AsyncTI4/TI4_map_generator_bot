package ti4.ai.trade;

import java.util.OptionalInt;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class Desperation {

    private static final double WIN_VALUE = 30;
    private static final int MAX_SHORT = 3;
    private static final double VP_BASE = 6;
    private static final double VP_CAP = 12;
    private static final double VP_STEP = 2;
    private static final double CONTENDER_BONUS = 2;
    private static final int RISES_FROM_GOAL = 4;
    private static final int CONTENDER_FROM_GOAL = 2;
    private static final double ACTION_FACTOR = 1.0;
    private static final double LATER_FACTOR = 0.5;
    private static final double NO_FACTOR = 0;
    private static final double SPEND_CAP_SHARE = 0.75;

    static double vpWorth(Game game, Player player) {
        int beyond = Math.max(0, Standings.vp(player) - (Standings.goal(game) - RISES_FROM_GOAL));
        double worth = VP_BASE + VP_STEP * beyond + (contenderExists(game) ? CONTENDER_BONUS : 0);
        return Math.min(VP_CAP, worth);
    }

    static boolean contenderExists(Game game) {
        return Standings.anyoneAtLeast(game, Standings.goal(game) - CONTENDER_FROM_GOAL);
    }

    static double scoreValue(Game game, Player player, int points) {
        if (points > 0 && Standings.vp(player) + points >= Standings.goal(game)) return WIN_VALUE;
        return points * vpWorth(game, player);
    }

    static double phaseFactor(Game game) {
        if (TradeLegality.isActionPhase(game)) return ACTION_FACTOR;
        if (TradeLegality.isStrategyPhase(game) || TradeLegality.isAgendaPhase(game)) return LATER_FACTOR;
        return NO_FACTOR;
    }

    static OptionalInt tradeGoodsShort(Game game, Player seat) {
        if (!TradeLegality.isActionPhase(game)) return OptionalInt.empty();
        return IntStream.rangeClosed(1, MAX_SHORT)
                .filter(shortfall -> SpendUnlock.pointDelta(game, seat, shortfall) > 0)
                .findFirst();
    }

    static double term(Game game, Player seat, int netTradeGoods) {
        double factor = phaseFactor(game);
        if (factor == NO_FACTOR || netTradeGoods == 0) return 0;
        return factor * scoreValue(game, seat, SpendUnlock.pointDelta(game, seat, netTradeGoods));
    }

    static double spendCap(Game game, Player seat, int points) {
        return SPEND_CAP_SHARE * scoreValue(game, seat, points);
    }
}
