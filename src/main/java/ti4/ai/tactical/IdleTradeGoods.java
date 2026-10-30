package ti4.ai.tactical;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.TokenPurchase;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class IdleTradeGoods {

    static final int KEPT_TRADE_GOODS = 3;

    public static int surplus(Game game, Player seat) {
        int held = seat.getTg();
        SpendCost scoring = ScoringReserve.of(game, seat);
        SpendCost kept = scoring.plus(TokenPurchase.influenceToKeep(game, seat, scoring));
        int reserved =
                Wallet.of(game, seat).plan(kept).map(Wallet.Payment::tradeGoods).orElse(held);
        return Math.max(0, held - reserved - KEPT_TRADE_GOODS);
    }
}
