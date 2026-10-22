package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.SearchMemo;
import ti4.ai.scoring.PromisedTradeGoods;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.OpeningHold;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TradeBudget {

    private static final String RESERVED_MEMO = "budget.reservedTradeGoods";

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
        return SearchMemo.remember(() -> computeReservedTradeGoods(game, seat), RESERVED_MEMO, seat);
    }

    private static int computeReservedTradeGoods(Game game, Player seat) {
        SpendCost reserve = ScoringReserve.reserved(game, seat)
                .map(ScoringReserve.Reserved::cost)
                .orElse(SpendCost.NONE)
                .plus(OpeningHold.gravityDriveReserve(game, seat));
        if (reserve.isNone()) return 0;
        return Wallet.of(game, seat)
                .plan(reserve)
                .map(Wallet.Payment::tradeGoods)
                .orElse(0);
    }
}
