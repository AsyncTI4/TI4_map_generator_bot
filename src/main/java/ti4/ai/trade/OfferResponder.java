package ti4.ai.trade;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.trade.Stinginess.Veto;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class OfferResponder {

    private static final String COUNTERS_KEY = "tradeCounters|";
    private static final String KEY_SEPARATOR = "|";
    private static final int MAX_COUNTERS = 2;

    private enum Kind {
        ACCEPT,
        COUNTER,
        REJECT
    }

    private record Verdict(Kind kind, Deal counter, Purpose purpose, boolean honour) {

        static Verdict accepted() {
            return new Verdict(Kind.ACCEPT, Deal.EMPTY, Purpose.COUNTER, false);
        }

        static Verdict honoured() {
            return new Verdict(Kind.ACCEPT, Deal.EMPTY, Purpose.COUNTER, true);
        }

        static Verdict rejected() {
            return new Verdict(Kind.REJECT, Deal.EMPTY, Purpose.COUNTER, false);
        }

        static Verdict countered(Deal counter, Purpose purpose) {
            return new Verdict(Kind.COUNTER, counter, purpose, false);
        }
    }

    static Optional<AiDecision> answer(AiTurnContext context) {
        if (PaymentRules.isPending(context)) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        for (IncomingOffer offer : IncomingOffer.visible(context)) {
            if (!offer.untouched(context) || offer.closed(context)) continue;
            Optional<Player> offerer = offer.offerer(game);
            if (offerer.isEmpty()) continue;
            if (!DealItem.tradable(offerer.get().getFaction())) return reject(context, offer, offerer.get());
            if (!offer.isCurrent(game, seat)) {
                offer.markStale(context);
                continue;
            }
            Deal deal = offer.deal(offerer.get(), seat);
            if (changedSinceSeen(context, offer, deal)) {
                offer.markStale(context);
                continue;
            }
            if (waitsFor(context, offerer.get())) continue;
            Verdict verdict = verdict(context, offerer.get(), deal);
            if (verdict.kind() == Kind.COUNTER && OfferBuilder.active(context)) continue;
            return execute(context, offer, offerer.get(), deal, verdict);
        }
        return Optional.empty();
    }

    private static boolean changedSinceSeen(AiTurnContext context, IncomingOffer offer, Deal deal) {
        String seen = offer.seen(context);
        if (seen.isEmpty()) {
            offer.markSeen(context, deal.fingerprint());
            return false;
        }
        return !seen.equals(deal.fingerprint());
    }

    private static boolean waitsFor(AiTurnContext context, Player offerer) {
        return PendingOffers.with(context, offerer)
                || OfferBuilder.partner(context)
                        .filter(offerer.getFaction()::equals)
                        .isPresent();
    }

    private static Verdict verdict(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (!AiSettings.isTradingEnabled()) return Verdict.rejected();
        List<Veto> vetoes = Stinginess.vetoes(game, seat, offerer, deal);
        if (vetoes.contains(Veto.ILLEGAL) || vetoes.contains(Veto.TWO_NOTES)) return Verdict.rejected();
        if (!TradeValue.theirSideCovered(seat, offerer, deal)) return Verdict.rejected();
        if (TradeCardRules.honours(context, offerer, deal) && TradeValue.commitmentOk(context, offerer, deal)) {
            return Verdict.honoured();
        }
        if (DebtRules.owedCollection(context, offerer, deal)) return collection(context, offerer, deal);
        if (DebtRules.repays(context, offerer, deal) && TradeValue.commitmentOk(context, offerer, deal)) {
            return Verdict.accepted();
        }
        double trust = Trust.of(context, offerer);
        if (deal.hasUnsupported()) return withoutUnsupported(context, offerer, deal);
        if (!TradeValue.ourSideCovered(context, offerer, deal)) return nearest(context, offerer, deal);
        if (isLoan(seat, offerer, deal) && !AiSeats.isAiSeat(offerer) && trust < Trust.LENDING) {
            return Verdict.rejected();
        }
        if (TradeValue.of(game, seat, offerer, deal, trust).clears(TradeValue.margin(offerer))) {
            return Verdict.accepted();
        }
        return nearest(context, offerer, deal);
    }

    private static Verdict collection(AiTurnContext context, Player creditor, Deal deal) {
        if (TradeValue.commitmentOk(context, creditor, deal)) return Verdict.accepted();
        return DebtRules.payablePart(context, creditor, deal)
                .filter(part -> !part.sameAs(deal))
                .filter(part -> TradeValue.commitmentOk(context, creditor, part))
                .filter(part -> mayCounter(context, creditor))
                .map(part -> Verdict.countered(part, Purpose.DEBT_PAYMENT))
                .orElseGet(Verdict::rejected);
    }

    private static Verdict withoutUnsupported(AiTurnContext context, Player offerer, Deal deal) {
        Deal supported = CounterOffers.base(context, offerer, deal);
        boolean worth = !supported.isEmpty()
                && mayCounter(context, offerer)
                && TradeValue.acceptable(context.game(), context.seat(), offerer, supported, Trust.of(context, offerer))
                && Stinginess.partnerGain(context.game(), offerer, context.seat(), supported) >= 0;
        return worth ? Verdict.countered(supported, Purpose.COUNTER) : Verdict.rejected();
    }

    private static Verdict nearest(AiTurnContext context, Player offerer, Deal deal) {
        if (!mayCounter(context, offerer)) return Verdict.rejected();
        return CounterOffers.nearest(context, offerer, deal)
                .map(counter -> Verdict.countered(counter, Purpose.COUNTER))
                .orElseGet(Verdict::rejected);
    }

    private static boolean isLoan(Player seat, Player offerer, Deal deal) {
        return deal.total(seat.getFaction(), ItemType.TRADE_GOODS) > 0
                && deal.total(offerer.getFaction(), ItemType.SEND_DEBT) > 0;
    }

    private static boolean mayCounter(AiTurnContext context, Player offerer) {
        return TradeMemory.count(context.memory(), countersKey(context, offerer)) < MAX_COUNTERS;
    }

    private static String countersKey(AiTurnContext context, Player offerer) {
        return COUNTERS_KEY + TradeLegality.window(context.game()) + KEY_SEPARATOR + offerer.getFaction();
    }

    private static Optional<AiDecision> execute(
            AiTurnContext context, IncomingOffer offer, Player offerer, Deal deal, Verdict verdict) {
        return switch (verdict.kind()) {
            case ACCEPT -> accept(context, offer, offerer, deal, verdict.honour());
            case COUNTER -> counter(context, offer, offerer, verdict);
            case REJECT -> reject(context, offer, offerer);
        };
    }

    private static Optional<AiDecision> accept(
            AiTurnContext context, IncomingOffer offer, Player offerer, Deal deal, boolean honour) {
        Player seat = context.seat();
        if (!deal.isDebtOnly()) TradeLegality.useWindow(context, offerer);
        if (paysDebtToUs(seat, offerer, deal)) Trust.record(context, offerer, Trust.Event.PAID_DEBT);
        if (asksForAnyNote(seat, deal)) {
            NotesForTrade.leastHarmful(context.game(), seat, offerer, false)
                    .ifPresent(alias -> NoteGiving.owe(context, offerer.getFaction(), alias));
        }
        if (honour) TradeCardRules.honoured(context, offerer);
        offer.markAnswered(context);
        return Optional.of(
                AiDecision.press(offer.prompt(), offer.accept(), "trade: accept offer from " + offerer.getFaction()));
    }

    private static Optional<AiDecision> counter(
            AiTurnContext context, IncomingOffer offer, Player offerer, Verdict verdict) {
        Optional<AiDecision> start =
                OfferBuilder.startCounter(context, offer, offerer, verdict.counter(), verdict.purpose());
        if (start.isEmpty()) return reject(context, offer, offerer);
        TradeMemory.increment(context.memory(), countersKey(context, offerer));
        offer.markAnswered(context);
        return start;
    }

    private static Optional<AiDecision> reject(AiTurnContext context, IncomingOffer offer, Player offerer) {
        offer.markAnswered(context);
        return Optional.of(
                AiDecision.press(offer.prompt(), offer.reject(), "trade: reject offer from " + offerer.getFaction()));
    }

    private static boolean paysDebtToUs(Player seat, Player offerer, Deal deal) {
        String theirs = offerer.getFaction();
        int paid = deal.total(theirs, ItemType.TRADE_GOODS) + deal.total(theirs, ItemType.COMMODITIES);
        return paid > 0 && deal.total(seat.getFaction(), ItemType.CLEAR_DEBT) > 0;
    }

    private static boolean asksForAnyNote(Player seat, Deal deal) {
        return deal.items().stream().anyMatch(item -> item.isFrom(seat.getFaction()) && item.isGenericNote());
    }
}
