package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class DealSearch {

    static Optional<Deal> cheapest(AiTurnContext context, Player partner, List<Deal> candidates) {
        Game game = context.game();
        Player seat = context.seat();
        double trust = Trust.of(context, partner);
        boolean desperate = Desperation.tradeGoodsShort(game, seat).isPresent();
        return candidates.stream()
                .filter(deal -> TradeValue.coverable(game, seat, partner, deal))
                .filter(deal -> TradeValue.proposable(game, seat, partner, deal, trust))
                .min(Comparator.comparingDouble(deal -> giveCost(game, seat, partner, deal, trust, desperate)));
    }

    static double giveCost(Game game, Player seat, Player partner, Deal deal, double trust, boolean desperate) {
        return deal.items().stream()
                .filter(item -> item.isFrom(seat.getFaction()))
                .mapToDouble(item -> ComponentValues.give(game, seat, partner, item, trust, desperate))
                .sum();
    }
}
