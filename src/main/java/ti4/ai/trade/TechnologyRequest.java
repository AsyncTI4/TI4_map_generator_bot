package ti4.ai.trade;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.SearchMemo;
import ti4.ai.brain.StrategyCard;
import ti4.ai.strategy.OpeningNeeds;
import ti4.ai.strategy.TechnologyTiming;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TechnologyRequest {

    private static final String SPAN = "trade.techRequest";
    private static final int PAYMENT = 1;
    private static final int MAX_READ_PAYMENT = 2;
    private static final int LAST_REQUEST_ROUND = 1;
    private static final long ANNOUNCEMENT_TTL_MILLIS = Duration.ofMinutes(20).toMillis();
    private static final String ANNOUNCED_KEY = "technologyAskAnnounced|";
    private static final String SENT_KEY = "technologyRequestSent|";
    private static final char SEPARATOR = '|';
    private static final Set<ItemType> PAYMENT_TYPES = EnumSet.of(ItemType.TRADE_GOODS, ItemType.SEND_DEBT);

    enum Ask {
        EARLY,
        HOLD
    }

    record Plan(Player partner, Deal deal, Ask ask, boolean byHolder) {

        Purpose purpose() {
            return ask == Ask.EARLY ? Purpose.TECHNOLOGY_REQUEST : Purpose.TECHNOLOGY_HOLD;
        }

        String signature() {
            return partner.getFaction() + SEPARATOR + ask + SEPARATOR + byHolder;
        }

        String announcement(Player seat) {
            return byHolder
                    ? TechnologyAnnouncements.fee(seat, partner, deal)
                    : TechnologyAnnouncements.payment(seat, partner, deal, ask);
        }
    }

    static Optional<AiDecision> announce(AiTurnContext context, List<Player> partners) {
        try (Thinking.Span ignored = Thinking.enter(SPAN)) {
            return announcePlan(context, partners);
        }
    }

    private static Optional<AiDecision> announcePlan(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        if (context.memory().has(announcedKey(game)) || context.memory().has(sentKey(game))) return Optional.empty();
        Optional<Plan> plan = plan(context, partners);
        if (plan.isEmpty()) return Optional.empty();
        context.memory().put(announcedKey(game), plan.get().signature() + SEPARATOR + context.now());
        return Optional.of(new AiDecision.Announce(plan.get().announcement(context.seat())));
    }

    static Optional<AiDecision> start(AiTurnContext context, List<Player> partners) {
        try (Thinking.Span ignored = Thinking.enter(SPAN)) {
            return startPlan(context, partners);
        }
    }

    private static Optional<AiDecision> startPlan(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Optional<String> announced = context.memory().get(announcedKey(game));
        if (announced.isEmpty() || context.memory().has(sentKey(game))) return Optional.empty();
        Optional<Plan> plan = plan(context, partners);
        if (plan.isEmpty() || !stillAnnounced(context, announced.get(), plan.get())) {
            context.memory().put(sentKey(game), "abandoned");
            return Optional.empty();
        }
        Optional<AiDecision> start = OfferBuilder.start(
                context, plan.get().partner(), plan.get().deal(), plan.get().purpose());
        start.ifPresent(
                ignored -> context.memory().put(sentKey(game), plan.get().signature()));
        return start;
    }

    static Optional<Plan> plan(AiTurnContext context, List<Player> partners) {
        try (SearchMemo.Open ignored = SearchMemo.open()) {
            return search(context, partners);
        }
    }

    static Optional<Plan> search(AiTurnContext context, List<Player> partners) {
        if (!dealRound(context.game())) return Optional.empty();
        return requestEarly(context, partners)
                .or(() -> offerToWait(context, partners))
                .or(() -> askToWait(context, partners));
    }

    private static boolean stillAnnounced(AiTurnContext context, String announced, Plan plan) {
        String[] parts = StringUtils.split(announced, SEPARATOR);
        if (parts.length != 4 || !StringUtils.isNumeric(parts[3])) return false;
        String signature = String.join(String.valueOf(SEPARATOR), parts[0], parts[1], parts[2]);
        return signature.equals(plan.signature())
                && context.now() - Long.parseLong(parts[3]) <= ANNOUNCEMENT_TTL_MILLIS;
    }

    private static Optional<Plan> requestEarly(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        if (game.getRound() > LAST_REQUEST_ROUND) return Optional.empty();
        if (seat.getTg() < PAYMENT || !OpeningNeeds.wantsTechnologySoon(game, seat)) return Optional.empty();
        if (!OpeningNeeds.canFollowTechnologyAfterPaying(game, seat, PAYMENT)) return Optional.empty();
        return OpeningNeeds.technologyHolder(game, seat)
                .filter(partners::contains)
                .map(holder -> new Plan(
                        holder,
                        Deal.EMPTY.adjust(seat.getFaction(), holder.getFaction(), ItemType.TRADE_GOODS, PAYMENT),
                        Ask.EARLY,
                        false));
    }

    private static Optional<Plan> offerToWait(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Player> holder = OpeningNeeds.technologyHolder(game, seat).filter(partners::contains);
        if (holder.isEmpty() || !TechnologyTiming.deniable(game, seat)) return Optional.empty();
        double trust = Trust.of(context, holder.get());
        return payment(game, seat, holder.get())
                .filter(payment -> worthPaying(game, seat, holder.get(), payment, trust))
                .map(payment -> new Plan(holder.get(), payment, Ask.HOLD, false));
    }

    private static Optional<Plan> askToWait(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        if (!holdsTechnology(game, seat) || committed(context)) return Optional.empty();
        List<Player> payers = partners.stream()
                .filter(partner -> TechnologyTiming.deniable(game, partner))
                .filter(partner -> OwnTurnDeals.mayApproach(context, partner))
                .sorted(Comparator.comparingDouble((Player partner) -> TechnologyTiming.followSurplus(game, partner))
                        .reversed())
                .toList();
        for (Player payer : payers) {
            double trust = Trust.of(context, payer);
            Optional<Plan> plan = payment(game, payer, seat)
                    .filter(payment ->
                            TradeValue.of(game, seat, payer, payment, trust).selfGain()
                                    >= TechnologyTiming.holdCost(game, seat, payer))
                    .filter(payment -> worthPaying(game, payer, seat, payment, trust))
                    .map(payment -> new Plan(payer, payment, Ask.HOLD, true));
            if (plan.isPresent()) return plan;
        }
        return Optional.empty();
    }

    static Optional<Boolean> accepts(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (!dealRound(game)) return Optional.empty();
        if (holdsTechnology(game, seat) && pays(deal, offerer, seat)) {
            return askOf(context, offerer).map(ask -> acceptsAsk(context, offerer, deal, ask));
        }
        if (holdsTechnology(game, offerer) && pays(deal, seat, offerer)) {
            if (AiSeats.isAiSeat(offerer) && !TechnologyAnnouncements.feeAsked(context, offerer, seat)) {
                return Optional.empty();
            }
            return Optional.of(TechnologyTiming.deniable(game, seat)
                    && worthPaying(game, seat, offerer, deal, Trust.of(context, offerer)));
        }
        return Optional.empty();
    }

    static void noteAccepted(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (!dealRound(game) || !holdsTechnology(game, seat) || !pays(deal, offerer, seat)) return;
        askOf(context, offerer).ifPresent(ask -> {
            if (ask == Ask.HOLD) TechnologyTiming.grantHold(game, context.memory(), offerer);
            else context.memory().put(OpeningNeeds.technologyRequestedKey(game), offerer.getFaction());
        });
    }

    static void noteProposalAccepted(AiTurnContext context, Player partner, Purpose purpose) {
        Game game = context.game();
        if (purpose != Purpose.TECHNOLOGY_HOLD || !holdsTechnology(game, context.seat())) return;
        TechnologyTiming.grantHold(game, context.memory(), partner);
    }

    static Optional<Ask> askOf(AiTurnContext context, Player payer) {
        Optional<Ask> announced = TechnologyAnnouncements.paymentAsk(context, payer, context.seat());
        if (announced.isPresent() || AiSeats.isAiSeat(payer)) return announced;
        return Optional.of(TechnologyTiming.followsNow(context.game(), payer) ? Ask.EARLY : Ask.HOLD);
    }

    static Optional<Deal> payment(Game game, Player payer, Player holder) {
        String from = payer.getFaction();
        String to = holder.getFaction();
        int amount = TechnologyTiming.holdPayment(game, payer);
        if (TradeBudget.spareTradeGoods(game, payer, holder) >= amount
                && TechnologyTiming.followsLaterAfterPaying(game, payer, amount)) {
            return Optional.of(Deal.EMPTY.adjust(from, to, ItemType.TRADE_GOODS, amount));
        }
        if (DebtRules.room(payer, holder) >= amount) {
            return Optional.of(Deal.EMPTY.adjust(from, to, ItemType.SEND_DEBT, amount));
        }
        return Optional.empty();
    }

    private static boolean acceptsAsk(AiTurnContext context, Player offerer, Deal deal, Ask ask) {
        Game game = context.game();
        Player seat = context.seat();
        double paid = TradeValue.of(game, seat, offerer, deal, Trust.of(context, offerer))
                .selfGain();
        if (ask == Ask.HOLD) {
            return !context.memory().has(OpeningNeeds.technologyRequestedKey(game))
                    && paid >= TechnologyTiming.holdCost(game, seat, offerer);
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

    private static String announcedKey(Game game) {
        return ANNOUNCED_KEY + game.getRound();
    }

    private static String sentKey(Game game) {
        return SENT_KEY + game.getRound();
    }
}
