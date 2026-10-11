package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TradeUses {

    private static final int TRADE_GOODS_GAINED = 3;

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        Game game = in.game();
        Player seat = in.seat();
        int replenished = Math.max(0, seat.getCommoditiesTotal() - seat.getCommodities());
        int unlockedPoints = SpendUnlock.pointDelta(game, seat, TRADE_GOODS_GAINED);
        return List.of(
                new Benefit(CardUse.TRADE_GOODS, TRADE_GOODS_GAINED * CardValueScale.TRADE_GOOD),
                new Benefit(CardUse.TRADE_COMMODITIES, replenished * CardValueScale.COMMODITY),
                new Benefit(
                        CardUse.TRADE_SPEND_OBJECTIVE,
                        Math.max(0, unlockedPoints)
                                * CardValueScale.SPEND_UNLOCK_SHARE
                                * CardValueScale.VICTORY_POINT),
                new Benefit(CardUse.TRADE_LEVERAGE, hasTradePartner(game, seat) ? CardValueScale.TRADE_LEVERAGE : 0));
    }

    private static boolean hasTradePartner(Game game, Player seat) {
        return AiSettings.isTradingEnabled()
                && game.getRealPlayers().stream().anyMatch(other -> other != seat);
    }
}
