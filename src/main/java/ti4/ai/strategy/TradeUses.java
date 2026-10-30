package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.scoring.ExpectedTradeGoods;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TradeUses {

    private static final int TRADE_GOODS_GAINED = ExpeditionUnlock.TRADE_GOODS_FROM_TRADE;

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        Game game = in.game();
        Player seat = in.seat();
        int replenished = Math.max(0, seat.getCommoditiesTotal() - seat.getCommodities());
        int unlockedPoints = spendPointsUnlocked(game, seat);
        return List.of(
                new Benefit(CardUse.TRADE_GOODS, TRADE_GOODS_GAINED * CardValueScale.TRADE_GOOD),
                new Benefit(CardUse.TRADE_COMMODITIES, replenished * CardValueScale.COMMODITY),
                new Benefit(
                        CardUse.TRADE_SPEND_OBJECTIVE,
                        Math.max(0, unlockedPoints) * CardValueScale.SPEND_UNLOCK_SHARE * CardValueScale.VICTORY_POINT),
                new Benefit(CardUse.TRADE_LEVERAGE, hasTradePartner(game, seat) ? CardValueScale.TRADE_LEVERAGE : 0),
                new Benefit(
                        CardUse.TRADE_EXPEDITION,
                        expeditionValue(game, seat, unlockedPoints, in.moment() == PlanMoment.PICK)),
                new Benefit(
                        CardUse.OPENING_EXPANSION,
                        OpeningOutlook.gravityDriveGainFromTrade(game, seat, in.moment() == PlanMoment.PICK)));
    }

    private static double expeditionValue(Game game, Player seat, int unlockedPoints, boolean racing) {
        if (unlockedPoints > 0) return 0;
        return ExpeditionUnlock.afterTradeGoods(game, seat, TRADE_GOODS_GAINED, racing);
    }

    static int spendPointsUnlocked(Game game, Player seat) {
        int holding = SpendUnlock.pointDelta(game, seat, ExpectedTradeGoods.ifHoldingTrade(game, seat));
        int following = SpendUnlock.pointDelta(game, seat, ExpectedTradeGoods.ifFollowingTrade(game, seat));
        return Math.max(0, holding - following);
    }

    private static boolean hasTradePartner(Game game, Player seat) {
        return AiSettings.isTradingEnabled() && game.getRealPlayers().stream().anyMatch(other -> other != seat);
    }
}
