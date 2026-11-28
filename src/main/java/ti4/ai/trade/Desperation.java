package ti4.ai.trade;

import java.util.OptionalInt;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class Desperation {

    private static final int MAX_SHORT = 3;
    private static final double ACTION_FACTOR = 1.0;
    private static final double LATER_FACTOR = 0.5;
    private static final double NO_FACTOR = 0;
    private static final double SPEND_CAP_SHARE = 0.75;

    static double vpWorth(Game game, Player player) {
        return ScoringStakes.vpWorth(game, player);
    }

    static boolean contenderExists(Game game) {
        return ScoringStakes.contenderExists(game);
    }

    static double scoreValue(Game game, Player player, int points) {
        return ScoringStakes.scoreValue(game, player, points);
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
