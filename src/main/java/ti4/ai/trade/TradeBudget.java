package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.PromisedTradeGoods;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TradeBudget {

    static int promisedCommodities(Player seat) {
        return PromisedTradeGoods.sent(seat, ItemType.COMMODITIES.token(), null);
    }

    static int promisedCommodities(Player seat, Player receiver) {
        return PromisedTradeGoods.sent(seat, ItemType.COMMODITIES.token(), receiver.getFaction());
    }

    static int freeCommodities(AiTurnContext context) {
        Player seat = context.seat();
        return Math.max(
                0, seat.getCommodities() - promisedCommodities(seat) - TradeCardRules.reservedCommodities(context));
    }

    static int freeTradeGoods(Game game, Player seat) {
        return Math.max(0, seat.getTg() - reservedTradeGoods(game, seat) - PromisedTradeGoods.of(seat));
    }

    static int spareCommodities(Player seat, Player partner) {
        int promisedElsewhere = promisedCommodities(seat) - promisedCommodities(seat, partner);
        return Math.max(0, seat.getCommodities() - promisedElsewhere);
    }

    static int spareCommodities(AiTurnContext context, Player partner) {
        int spare = spareCommodities(context.seat(), partner);
        if (TradeCardRules.holdsFor(context, partner)) return spare;
        return Math.max(0, spare - TradeCardRules.reservedCommodities(context));
    }

    static int spareTradeGoods(Game game, Player seat, Player partner) {
        int promisedElsewhere = PromisedTradeGoods.of(seat) - PromisedTradeGoods.to(seat, partner);
        return Math.max(0, seat.getTg() - reservedTradeGoods(game, seat) - promisedElsewhere);
    }

    static int reservedTradeGoods(Game game, Player seat) {
        return ScoringReserve.reserved(game, seat)
                .flatMap(reserved -> Wallet.of(game, seat).plan(reserved.cost()))
                .map(Wallet.Payment::tradeGoods)
                .orElse(0);
    }
}
