package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class CardSales {

    static final int MAX_PRICE = 3;
    private static final String ROUND_KEY = "tradeCardSales|";

    private record Offer(Player partner, Deal deal, double utility) {}

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        String key = ROUND_KEY + game.getRound();
        if (context.memory().has(key)) return Optional.empty();
        for (Offer offer : best(context, partners)) {
            Optional<AiDecision> start = OfferBuilder.start(context, offer.partner(), offer.deal(), Purpose.SELL);
            if (start.isEmpty()) continue;
            context.memory().put(key, String.valueOf(context.now()));
            return start;
        }
        return Optional.empty();
    }

    static List<Deal> candidates(Game game, Player seat, Player partner) {
        Optional<String> spare = spareCard(game, seat);
        if (spare.isEmpty() || !TradeActionCards.tradable(seat, partner)) return List.of();
        DealItem card = TradeActionCards.item(seat, partner, spare.get());
        List<Deal> deals = new ArrayList<>();
        for (int price = 1; price <= Math.min(MAX_PRICE, partner.getTg()); price++) {
            deals.add(
                    Deal.EMPTY.with(card).adjust(partner.getFaction(), seat.getFaction(), ItemType.TRADE_GOODS, price));
        }
        return deals;
    }

    private static List<Offer> best(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        List<Offer> offers = new ArrayList<>();
        for (Player partner : partners) {
            if (!OwnTurnDeals.mayApproach(context, partner)) continue;
            double trust = Trust.of(context, partner);
            for (Deal deal : candidates(game, seat, partner)) {
                if (!TradeValue.coverable(game, seat, partner, deal)) continue;
                if (!TradeValue.proposable(game, seat, partner, deal, trust)) continue;
                offers.add(new Offer(
                        partner,
                        deal,
                        TradeValue.of(game, seat, partner, deal, trust).utility()));
            }
        }
        offers.sort(Comparator.comparingDouble(Offer::utility).reversed());
        return offers;
    }

    private static Optional<String> spareCard(Game game, Player seat) {
        if (!ActionCardValue.hasSpareCard(game, seat)) return Optional.empty();
        return ActionCardValue.discardOrder(game, seat).stream().findFirst();
    }
}
