package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class RaisableTradeGoods {

    public static int byWashing(Game game, Player seat) {
        if (!AiSettings.isTradingEnabled() || seat.getCommodities() < 1) return 0;
        return TradeLegality.legalPartners(game, seat).stream()
                .filter(AiSeats::isAiSeat)
                .mapToInt(partner -> Math.min(TradeBudget.spareCommodities(seat, partner), partner.getCommodities()))
                .max()
                .orElse(0);
    }

    public static int fromRivalTrade(Game game, Player seat) {
        if (!AiSettings.isTradingEnabled() || !TradeTerms.mastersOfTrade(seat)) return 0;
        return game.getRealPlayers().stream()
                .filter(holder -> holder != seat && AiSeats.isAiSeat(holder))
                .filter(holder -> holdsUnplayedTrade(game, holder))
                .filter(holder -> TradeLegality.canTransactInActionPhase(game, seat, holder))
                .mapToInt(holder -> Math.min(seat.getCommoditiesTotal(), holder.getCommoditiesTotal()))
                .max()
                .orElse(0);
    }

    private static boolean holdsUnplayedTrade(Game game, Player player) {
        return player.getSCs().stream()
                .anyMatch(initiative -> !game.getPlayedSCs().contains(initiative)
                        && StrategyCard.of(game, initiative) == StrategyCard.TRADE);
    }
}
