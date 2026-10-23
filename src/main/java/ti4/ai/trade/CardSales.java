package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.trace.Thinking;
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
        return candidates(game, seat, partner, ActionCardValue.spareCard(game, seat));
    }

    private static List<Deal> candidates(Game game, Player seat, Player partner, Optional<String> spare) {
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
        Optional<String> spare;
        try (Thinking.Span ignored = Thinking.enter("cardSales.spareCard")) {
            spare = ActionCardValue.spareCard(game, seat);
        }
        if (spare.isEmpty()) return offers;
        for (Player partner : partners) {
            if (!OwnTurnDeals.mayApproach(context, partner)) continue;
            double trust = Trust.of(context, partner);
            List<Deal> deals;
            try (Thinking.Span ignored = Thinking.enter("cardSales.candidates")) {
                deals = candidates(game, seat, partner, spare);
            }
            for (Deal deal : deals) {
                boolean coverable;
                try (Thinking.Span ignored = Thinking.enter("cardSales.coverable")) {
                    coverable = TradeValue.coverable(game, seat, partner, deal);
                }
                if (!coverable) continue;
                boolean proposable;
                try (Thinking.Span ignored = Thinking.enter("cardSales.proposable")) {
                    proposable = TradeValue.proposable(game, seat, partner, deal, trust);
                }
                if (!proposable) continue;
                double utility;
                try (Thinking.Span ignored = Thinking.enter("cardSales.value")) {
                    utility = TradeValue.of(game, seat, partner, deal, trust).utility();
                }
                offers.add(new Offer(partner, deal, utility));
            }
        }
        offers.sort(Comparator.comparingDouble(Offer::utility).reversed());
        return offers;
    }
}
