package ti4.ai.trade;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.StrategyCard;
import ti4.ai.strategy.OpeningNeeds;
import ti4.ai.strategy.TechnologyTiming;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TechnologyRequest {

    private static final int PAYMENT = 1;
    private static final int MAX_READ_PAYMENT = 2;
    private static final int LAST_REQUEST_ROUND = 1;
    private static final String SENT_KEY = "technologyRequestSent|";
    private static final String HOLD_SENT_KEY = "technologyHoldSent|";
    private static final Set<ItemType> PAYMENT_TYPES = EnumSet.of(ItemType.TRADE_GOODS, ItemType.SEND_DEBT);

    enum Ask {
        EARLY,
        HOLD
    }

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        String sentKey = SENT_KEY + game.getRound();
        if (game.getRound() > LAST_REQUEST_ROUND || context.memory().has(sentKey)) return Optional.empty();
        if (seat.getTg() < PAYMENT || !OpeningNeeds.wantsTechnologySoon(game, seat)) return Optional.empty();
        if (!OpeningNeeds.canFollowTechnologyAfterPaying(game, seat, PAYMENT)) return Optional.empty();
        Optional<Player> holder = OpeningNeeds.technologyHolder(game, seat).filter(partners::contains);
        if (holder.isEmpty()) return Optional.empty();
        Deal deal = Deal.EMPTY.adjust(seat.getFaction(), holder.get().getFaction(), ItemType.TRADE_GOODS, PAYMENT);
        Optional<AiDecision> start = OfferBuilder.start(context, holder.get(), deal, Purpose.TECHNOLOGY_REQUEST);
        start.ifPresent(ignored -> context.memory().put(sentKey, holder.get().getFaction()));
        return start;
    }

    static Optional<AiDecision> offerToWait(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        String sentKey = HOLD_SENT_KEY + game.getRound();
        if (!dealRound(game) || context.memory().has(sentKey) || !TechnologyTiming.deniable(game, seat)) {
            return Optional.empty();
        }
        Optional<Player> holder = OpeningNeeds.technologyHolder(game, seat).filter(partners::contains);
        if (holder.isEmpty()) return Optional.empty();
        Optional<Deal> deal = payment(game, seat, holder.get())
                .filter(payment -> worthPaying(game, seat, holder.get(), payment, Trust.of(context, holder.get())));
        if (deal.isEmpty()) return Optional.empty();
        Optional<AiDecision> start = OfferBuilder.start(context, holder.get(), deal.get(), Purpose.TECHNOLOGY_HOLD);
        start.ifPresent(ignored -> context.memory().put(sentKey, holder.get().getFaction()));
        return start;
    }

    static Optional<AiDecision> askToWait(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        String sentKey = HOLD_SENT_KEY + game.getRound();
        if (!dealRound(game) || context.memory().has(sentKey) || !holdsTechnology(game, seat)) return Optional.empty();
        if (committed(context)) return Optional.empty();
        double holdCost = TechnologyTiming.holdCost(game, seat);
        List<Player> payers = partners.stream()
                .filter(partner -> TechnologyTiming.deniable(game, partner))
                .filter(partner -> OwnTurnDeals.mayApproach(context, partner))
                .sorted(Comparator.comparingDouble((Player partner) -> TechnologyTiming.followSurplus(game, partner))
                        .reversed())
                .toList();
        for (Player payer : payers) {
            double trust = Trust.of(context, payer);
            Optional<Deal> deal = payment(game, payer, seat)
                    .filter(payment ->
                            TradeValue.of(game, seat, payer, payment, trust).selfGain() >= holdCost)
                    .filter(payment -> worthPaying(game, payer, seat, payment, trust));
            if (deal.isEmpty()) continue;
            Optional<AiDecision> start = OfferBuilder.start(context, payer, deal.get(), Purpose.TECHNOLOGY_HOLD);
            if (start.isPresent()) {
                context.memory().put(sentKey, payer.getFaction());
                return start;
            }
        }
        return Optional.empty();
    }

    static Optional<Boolean> accepts(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (!dealRound(game)) return Optional.empty();
        if (holdsTechnology(game, seat) && pays(deal, offerer, seat)) {
            return Optional.of(acceptsAsk(context, offerer, deal));
        }
        if (holdsTechnology(game, offerer) && pays(deal, seat, offerer)) {
            return Optional.of(TechnologyTiming.deniable(game, seat)
                    && worthPaying(game, seat, offerer, deal, Trust.of(context, offerer)));
        }
        return Optional.empty();
    }

    static void noteAccepted(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (!dealRound(game) || !holdsTechnology(game, seat) || !pays(deal, offerer, seat)) return;
        String key = ask(game, offerer) == Ask.HOLD
                ? TechnologyTiming.heldKey(game)
                : OpeningNeeds.technologyRequestedKey(game);
        context.memory().put(key, offerer.getFaction());
    }

    static void noteProposalAccepted(AiTurnContext context, Player partner, Purpose purpose) {
        Game game = context.game();
        if (purpose != Purpose.TECHNOLOGY_HOLD || !holdsTechnology(game, context.seat())) return;
        context.memory().put(TechnologyTiming.heldKey(game), partner.getFaction());
    }

    static Ask ask(Game game, Player offerer) {
        return TechnologyTiming.followsNow(game, offerer) ? Ask.EARLY : Ask.HOLD;
    }

    static Optional<Deal> payment(Game game, Player payer, Player holder) {
        String from = payer.getFaction();
        String to = holder.getFaction();
        if (TradeBudget.spareTradeGoods(game, payer, holder) >= PAYMENT
                && TechnologyTiming.followsLaterAfterPaying(game, payer, PAYMENT)) {
            return Optional.of(Deal.EMPTY.adjust(from, to, ItemType.TRADE_GOODS, PAYMENT));
        }
        if (DebtRules.room(payer, holder) >= PAYMENT) {
            return Optional.of(Deal.EMPTY.adjust(from, to, ItemType.SEND_DEBT, PAYMENT));
        }
        return Optional.empty();
    }

    private static boolean acceptsAsk(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        double paid = TradeValue.of(game, seat, offerer, deal, Trust.of(context, offerer))
                .selfGain();
        if (ask(game, offerer) == Ask.HOLD) {
            return !context.memory().has(OpeningNeeds.technologyRequestedKey(game))
                    && paid >= TechnologyTiming.holdCost(game, seat);
        }
        return !context.memory().has(TechnologyTiming.heldKey(game)) && paid >= TechnologyTiming.earlyCost(game, seat);
    }

    private static boolean worthPaying(Game game, Player payer, Player holder, Deal deal, double trust) {
        double cost = -TradeValue.of(game, payer, holder, deal, trust).selfGain();
        return TechnologyTiming.followsLaterAfterPaying(
                        game, payer, deal.total(payer.getFaction(), ItemType.TRADE_GOODS))
                && TechnologyTiming.followSurplus(game, payer) >= cost;
    }

    private static boolean committed(AiTurnContext context) {
        Game game = context.game();
        return context.memory().has(TechnologyTiming.heldKey(game))
                || context.memory().has(OpeningNeeds.technologyRequestedKey(game));
    }

    private static boolean pays(Deal deal, Player payer, Player receiver) {
        String from = payer.getFaction();
        String to = receiver.getFaction();
        int paid = deal.items().stream().mapToInt(DealItem::amount).sum();
        return paid >= 1
                && paid <= MAX_READ_PAYMENT
                && deal.items().stream()
                        .allMatch(item -> item.isFrom(from) && item.isTo(to) && PAYMENT_TYPES.contains(item.type()));
    }

    private static boolean holdsTechnology(Game game, Player player) {
        return TechnologyTiming.holdsUnplayed(game, player, StrategyCard.TECHNOLOGY);
    }

    private static boolean dealRound(Game game) {
        return game.getRound() <= TechnologyTiming.LAST_DEAL_ROUND;
    }
}
