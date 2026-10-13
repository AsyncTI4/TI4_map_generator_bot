package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
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
}
