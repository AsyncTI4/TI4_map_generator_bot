package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class OwnTurnDeals {

    static final int OWN_TURN_DRAFTS = 2;
    static final int MAX_PENDING = 3;
    static final int MAX_TRADE_DRAFTS_PER_HOUR = 12;
    static final int MAX_DESPERATION_BUYS = 2;
    private static final int MAX_UNSOLICITED = 1;
    private static final int EXTRA_COMMODITIES = 3;
    private static final int MAX_DEBT_TOPPING = 3;
    private static final int EXTRA_DEBT = 3;
    private static final int MIN_SALE = 3;
    private static final double MAX_LENDER_RIVALRY = 0.6;
    private static final String TURN_DRAFTS_KEY = "tradeTurnDrafts|";
    private static final String DESPERATION_KEY = "tradeDesperation|";
    private static final String UNSOLICITED_KEY = "tradeUnsolicited|";
    private static final String COLLECTED_KEY = "tradeCollected|";
    private static final String KEY_SEPARATOR = "|";

    private record Option(Player partner, Deal deal, double score) {}

    static Optional<AiDecision> start(AiTurnContext context) {
        if (!mayStart(context)) return Optional.empty();
        Game game = context.game();
        if (TradeLegality.isActionPhase(game)) return ownTurn(context);
        if (TradeLegality.isAgendaPhase(game)) return agenda(context);
        return Optional.empty();
    }

    private static boolean mayStart(AiTurnContext context) {
        return AiSettings.isTradingEnabled()
                && !OfferBuilder.active(context)
                && OfferBuilder.startsInLastHour(context) < MAX_TRADE_DRAFTS_PER_HOUR
                && PendingOffers.count(context, purpose -> !purpose.settles()) < MAX_PENDING;
    }

    private static Optional<AiDecision> ownTurn(AiTurnContext context) {
        if (!startOfOwnTurn(context)) return Optional.empty();
        List<Player> partners = partners(context);
        if (partners.isEmpty()) return Optional.empty();
        Optional<AiDecision> payment = payDebt(context, partners);
        if (payment.isPresent()) return payment;
        if (TradeMemory.count(context.memory(), turnDraftsKey(context)) >= OWN_TURN_DRAFTS) return Optional.empty();
        Optional<AiDecision> started = desperation(context, partners)
                .or(() -> wash(context, partners, partner -> true))
                .or(() -> sell(context, partners))
                .or(() -> collect(context, partners));
        started.ifPresent(ignored -> TradeMemory.increment(context.memory(), turnDraftsKey(context)));
        return started;
    }

    private static Optional<AiDecision> agenda(AiTurnContext context) {
        List<Player> partners = partners(context);
        if (partners.isEmpty()) return Optional.empty();
        String ours = context.faction();
        List<Player> creditors = partners.stream()
                .filter(creditor -> !DebtRules.paymentPromptVisible(context, creditor))
                .toList();
        return payDebt(context, creditors)
                .or(() -> collect(context, partners))
                .or(() -> wash(
                        context,
                        partners,
                        partner -> AiSeats.isAiSeat(partner) && ours.compareTo(partner.getFaction()) < 0))
                .or(() -> wash(context, partners, partner -> !AiSeats.isAiSeat(partner)));
    }

    private static boolean startOfOwnTurn(AiTurnContext context) {
        return context.isActivePlayer()
                && !TacticalRules.inProgress(context.game(), context.seat())
                && !TacticalRules.actionTaken(context)
                && !StrategyCardRules.playedThisTurn(context)
                && !PaymentRules.isPending(context);
    }

    private static List<Player> partners(AiTurnContext context) {
        return TradeLegality.legalPartners(context.game(), context.seat()).stream()
                .filter(partner -> !TradeLegality.windowUsed(context, partner))
                .filter(partner -> !PendingOffers.with(context, partner))
                .filter(partner -> !IncomingOffer.liveFrom(context, partner))
                .filter(partner -> !TradeCardRules.holdsFor(context, partner))
                .toList();
    }

    private static Optional<AiDecision> payDebt(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        List<Player> creditors = partners.stream()
                .filter(creditor -> creditor.getDebtTokenCount(seat.getColor()) >= 1)
                .sorted(Comparator.comparingInt((Player creditor) -> creditor.getDebtTokenCount(seat.getColor()))
                        .reversed())
                .toList();
        for (Player creditor : creditors) {
            Optional<Deal> deal = DebtRules.payment(
                            seat,
                            creditor,
                            TradeBudget.freeTradeGoods(game, seat),
                            TradeBudget.freeCommodities(context))
                    .filter(payment -> !DebtRules.defers(game, creditor, payment))
                    .filter(payment -> TradeValue.commitmentOk(game, seat, creditor, payment));
            if (deal.isEmpty()) continue;
            Optional<AiDecision> start = OfferBuilder.start(context, creditor, deal.get(), Purpose.DEBT_PAYMENT);
            if (start.isPresent()) return start;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> desperation(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        String key = DESPERATION_KEY + game.getRound();
        OptionalInt shortBy = Desperation.tradeGoodsShort(game, seat);
        if (shortBy.isEmpty() || TradeMemory.count(context.memory(), key) >= MAX_DESPERATION_BUYS) {
            return Optional.empty();
        }
        int needed = shortBy.getAsInt();
        double spendCap = Desperation.spendCap(game, seat, SpendUnlock.pointDelta(game, seat, needed));
        for (Player lender : lenders(context, partners, needed)) {
            Optional<Deal> deal = DealSearch.cheapest(context, lender, packages(context, lender, needed, spendCap));
            if (deal.isEmpty()) continue;
            Optional<AiDecision> start = OfferBuilder.start(context, lender, deal.get(), Purpose.DESPERATION);
            if (start.isPresent()) {
                TradeMemory.increment(context.memory(), key);
                return start;
            }
        }
        return Optional.empty();
    }

    private static List<Player> lenders(AiTurnContext context, List<Player> partners, int needed) {
        Game game = context.game();
        Player seat = context.seat();
        Comparator<Player> aiFirst = Comparator.comparing(partner -> !AiSeats.isAiSeat(partner));
        Comparator<Player> leastRivalry =
                Comparator.comparingDouble(partner -> Stinginess.rivalry(game, seat, partner));
        Comparator<Player> richest = Comparator.comparingInt(Player::getTg).reversed();
        return partners.stream()
                .filter(partner -> partner.getTg() >= needed)
                .filter(partner -> Stinginess.rivalry(game, seat, partner) < MAX_LENDER_RIVALRY)
                .filter(partner -> AiSeats.isAiSeat(partner) || Trust.of(context, partner) >= Trust.LENDING)
                .sorted(aiFirst.thenComparing(leastRivalry).thenComparing(richest))
                .toList();
    }

    static List<Deal> packages(AiTurnContext context, Player lender, int needed, double spendCap) {
        Game game = context.game();
        Player seat = context.seat();
        String ours = seat.getFaction();
        String theirs = lender.getFaction();
        int commodities = TradeBudget.freeCommodities(context);
        int debtRoom = DebtRules.room(seat, lender);
        Deal ask = Deal.EMPTY.adjust(theirs, ours, ItemType.TRADE_GOODS, needed);
        Deal allCommodities = ask.adjust(ours, theirs, ItemType.COMMODITIES, commodities);
        List<Deal> packages = new ArrayList<>();
        for (int given = needed; given <= Math.min(commodities, needed + EXTRA_COMMODITIES); given++) {
            packages.add(ask.adjust(ours, theirs, ItemType.COMMODITIES, given));
        }
        for (int debt = 1; debt <= Math.min(debtRoom, MAX_DEBT_TOPPING); debt++) {
            packages.add(allCommodities.adjust(ours, theirs, ItemType.SEND_DEBT, debt));
        }
        for (int debt = needed; debt <= Math.min(debtRoom, needed + EXTRA_DEBT); debt++) {
            packages.add(ask.adjust(ours, theirs, ItemType.SEND_DEBT, debt));
        }
        NotesForTrade.leastHarmful(game, seat, lender, true)
                .map(alias -> seat.getPromissoryNotes().get(alias))
                .filter(Objects::nonNull)
                .ifPresent(id -> packages.add(
                        allCommodities.with(new DealItem(ours, theirs, ItemType.PROMISSORY, String.valueOf(id)))));
        double trust = Trust.of(context, lender);
        Map<String, Deal> distinct = new LinkedHashMap<>();
        for (Deal deal : packages) {
            if (DealSearch.giveCost(game, seat, lender, deal, trust, true) <= spendCap) {
                distinct.putIfAbsent(deal.fingerprint(), deal);
            }
        }
        return List.copyOf(distinct.values());
    }

    private static Optional<AiDecision> wash(AiTurnContext context, List<Player> partners, Predicate<Player> eligible) {
        Game game = context.game();
        Player seat = context.seat();
        int commodities = TradeBudget.freeCommodities(context);
        if (commodities < 1) return Optional.empty();
        String ours = seat.getFaction();
        List<Option> options = partners.stream()
                .filter(eligible)
                .filter(partner -> partner.getCommodities() >= 1)
                .filter(partner -> mayApproach(context, partner))
                .map(partner -> {
                    int washed = Math.min(commodities, partner.getCommodities());
                    Deal deal = Deal.EMPTY
                            .adjust(partner.getFaction(), ours, ItemType.COMMODITIES, washed)
                            .adjust(ours, partner.getFaction(), ItemType.COMMODITIES, washed);
                    double score = washed * (1 - Stinginess.rivalry(game, seat, partner));
                    return new Option(partner, deal, score);
                })
                .sorted(Comparator.comparingDouble(Option::score).reversed())
                .toList();
        return propose(context, options, Purpose.WASH);
    }

    private static Optional<AiDecision> sell(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        int commodities = TradeBudget.freeCommodities(context);
        if (commodities < MIN_SALE || partners.stream().anyMatch(partner -> partner.getCommodities() >= 1)) {
            return Optional.empty();
        }
        int price = commodities - 1;
        String ours = seat.getFaction();
        List<Option> options = partners.stream()
                .filter(partner -> partner.getTg() >= price)
                .filter(partner -> mayApproach(context, partner))
                .map(partner -> new Option(
                        partner,
                        Deal.EMPTY
                                .adjust(partner.getFaction(), ours, ItemType.TRADE_GOODS, price)
                                .adjust(ours, partner.getFaction(), ItemType.COMMODITIES, commodities),
                        1 - Stinginess.rivalry(game, seat, partner)))
                .sorted(Comparator.comparingDouble(Option::score).reversed())
                .toList();
        return propose(context, options, Purpose.SELL);
    }

    private static Optional<AiDecision> propose(AiTurnContext context, List<Option> options, Purpose purpose) {
        Game game = context.game();
        Player seat = context.seat();
        for (Option option : options) {
            Player partner = option.partner();
            if (!TradeValue.coverable(game, seat, partner, option.deal())
                    || !TradeValue.proposable(game, seat, partner, option.deal(), Trust.of(context, partner))) {
                continue;
            }
            Optional<AiDecision> start = OfferBuilder.start(context, partner, option.deal(), purpose);
            if (start.isPresent()) {
                if (!AiSeats.isAiSeat(partner))
                    TradeMemory.increment(context.memory(), unsolicitedKey(context, partner));
                return start;
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> collect(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        for (Player debtor : partners) {
            String key = COLLECTED_KEY + game.getRound() + KEY_SEPARATOR + debtor.getFaction();
            if (AiSeats.isAiSeat(debtor)
                    || seat.getDebtTokenCount(debtor.getColor()) < 1
                    || debtor.getCommodities() + debtor.getTg() < 1
                    || context.memory().has(key)) {
                continue;
            }
            Optional<Deal> deal = DebtRules.collection(seat, debtor)
                    .filter(collection -> TradeValue.commitmentOk(game, seat, debtor, collection));
            if (deal.isEmpty()) continue;
            Optional<AiDecision> start = OfferBuilder.start(context, debtor, deal.get(), Purpose.DEBT_COLLECTION);
            if (start.isPresent()) {
                context.memory().put(key, String.valueOf(context.now()));
                return start;
            }
        }
        return Optional.empty();
    }

    private static boolean mayApproach(AiTurnContext context, Player partner) {
        if (AiSeats.isAiSeat(partner)) return true;
        return Trust.of(context, partner) >= Trust.UNSOLICITED
                && TradeMemory.count(context.memory(), unsolicitedKey(context, partner)) < MAX_UNSOLICITED;
    }

    private static String unsolicitedKey(AiTurnContext context, Player partner) {
        return UNSOLICITED_KEY + context.game().getRound() + KEY_SEPARATOR + partner.getFaction();
    }

    private static String turnDraftsKey(AiTurnContext context) {
        return TURN_DRAFTS_KEY + context.turnKey();
    }
}
