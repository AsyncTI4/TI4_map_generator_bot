package ti4.ai.trade;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class PendingOffers {

    private static final long SEND_LANDED_GRACE_MILLIS = Duration.ofSeconds(60).toMillis();
    private static final long AI_REPLY_TIMEOUT_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long HUMAN_LONG_REPLY_TIMEOUT_MILLIS =
            Duration.ofHours(24).toMillis();
    private static final long HUMAN_REPLY_TIMEOUT_MILLIS = Duration.ofHours(12).toMillis();
    private static final String RESCIND = "rescindOffer_";
    private static final String BLACK_MARKET = "_BMD_";
    private static final String ACCEPTED = "accepted";
    private static final String CLOSED_KEY = "tradeOffersClosed|";

    private enum Stale {
        CROSSED("their offer crossed ours"),
        EXPIRED("no answer in time"),
        WINDOW_CLOSED("the trading window closed"),
        UNCOVERABLE("it can no longer be covered");

        private final String reason;

        Stale(String reason) {
            this.reason = reason;
        }
    }

    static void observe(AiTurnContext context) {
        for (Player partner : partners(context)) {
            Optional<Pending> pending = Pending.read(context.memory(), partner);
            if (pending.isPresent()) {
                follow(context, partner, pending.get());
            } else {
                adoptOrphan(context, partner);
            }
        }
    }

    static Optional<AiDecision> rescindStale(AiTurnContext context) {
        for (Player partner : partners(context)) {
            Optional<Pending> pending = Pending.read(context.memory(), partner);
            if (pending.isEmpty()) continue;
            Optional<Stale> stale = staleness(context, partner, pending.get());
            if (stale.isEmpty()) continue;
            Optional<AiDecision> rescind = rescind(context, partner, pending.get(), stale.get());
            if (rescind.isPresent()) return rescind;
        }
        return Optional.empty();
    }

    static boolean with(AiTurnContext context, Player partner) {
        return Pending.read(context.memory(), partner).isPresent();
    }

    static int count(AiTurnContext context, Predicate<Purpose> counted) {
        return (int) partners(context).stream()
                .map(partner -> Pending.read(context.memory(), partner))
                .flatMap(Optional::stream)
                .filter(pending -> counted.test(pending.purpose()))
                .count();
    }

    static void record(AiTurnContext context, Player partner, Purpose purpose, Deal deal) {
        long now = context.now();
        new Pending(
                        sentNumber(context, partner) + 1,
                        now,
                        purpose,
                        now + replyTimeout(partner, purpose),
                        TradeLegality.openKey(context.game(), purpose, deal),
                        "")
                .write(context.memory(), partner);
    }

    static void markNoneOpen(AiTurnContext context, Player partner) {
        markClosedUpTo(context, partner, sentNumber(context, partner));
    }

    private static List<Player> partners(AiTurnContext context) {
        return Standings.others(context.game(), context.seat()).stream()
                .filter(partner -> DealItem.tradable(partner.getFaction()))
                .toList();
    }

    private static void close(AiTurnContext context, Player partner, int closedUpTo) {
        Pending.drop(context.memory(), partner);
        markClosedUpTo(context, partner, closedUpTo);
    }

    private static void markClosedUpTo(AiTurnContext context, Player partner, int offerNumber) {
        TradeMemory.rewrite(context.memory(), closedKey(partner), String.valueOf(offerNumber));
    }

    private static boolean sentSinceClosed(AiTurnContext context, Player partner) {
        OptionalInt closed =
                TradeMemory.parseInt(context.memory().get(closedKey(partner)).orElse(""));
        return closed.isEmpty() || sentNumber(context, partner) > closed.getAsInt();
    }

    private static String closedKey(Player partner) {
        return CLOSED_KEY + partner.getFaction();
    }

    private static void follow(AiTurnContext context, Player partner, Pending pending) {
        int sent = sentNumber(context, partner);
        if (sent < pending.offerNumber()) {
            if (context.now() - pending.sentAt() > SEND_LANDED_GRACE_MILLIS) {
                close(context, partner, sent);
            }
            return;
        }
        if (sent > pending.offerNumber()) {
            close(context, partner, pending.offerNumber());
            return;
        }
        if (Deal.between(context.seat(), partner).isEmpty()) {
            close(context, partner, sent);
            accepted(context, partner, pending);
            return;
        }
        if (pending.rescindTarget().isEmpty()) {
            newestRescind(context, partner, pending.sentAt() - BuilderPrompts.SAME_STEP_TOLERANCE_MILLIS)
                    .ifPresent(match -> pending.withRescindTarget(match.prompt(), match.button())
                            .write(context.memory(), partner));
        }
    }

    private static void accepted(AiTurnContext context, Player partner, Pending pending) {
        Trust.record(context, partner, Trust.Event.ACCEPTED_OFFER);
        TradeLegality.useWindow(context, partner);
        if (pending.purpose().settles()) {
            context.memory().put(TradeCardRules.settledKey(context.game(), partner), ACCEPTED);
        }
    }

    private static void adoptOrphan(AiTurnContext context, Player partner) {
        Deal current = Deal.between(context.seat(), partner);
        if (current.isEmpty()
                || OfferBuilder.partner(context)
                        .filter(partner.getFaction()::equals)
                        .isPresent()
                || IncomingOffer.liveFrom(context, partner)) {
            return;
        }
        Optional<Match> rescind = unansweredRescind(context, partner);
        if (rescind.isEmpty()) {
            OfferBuilder.queueCleanup(context, partner);
            return;
        }
        long sentAt = rescind.get().prompt().createdAtMillis();
        new Pending(
                        sentNumber(context, partner),
                        sentAt,
                        Purpose.ADOPTED,
                        sentAt + replyTimeout(partner, Purpose.ADOPTED),
                        TradeLegality.openKey(context.game(), Purpose.ADOPTED, current),
                        "")
                .withRescindTarget(rescind.get().prompt(), rescind.get().button())
                .write(context.memory(), partner);
    }

    private static Optional<Match> unansweredRescind(AiTurnContext context, Player partner) {
        if (!sentSinceClosed(context, partner)) return Optional.empty();
        long lastBuilt = BuilderPrompts.newestBuildAt(context, partner);
        long oldestLive = context.now() - replyTimeout(partner, Purpose.ADOPTED);
        return newestRescind(context, partner, oldestLive)
                .filter(match -> match.prompt().createdAtMillis() > lastBuilt);
    }

    private static Optional<Stale> staleness(AiTurnContext context, Player partner, Pending pending) {
        Game game = context.game();
        Player seat = context.seat();
        Deal deal = Deal.between(seat, partner);
        if (crossed(context, partner, pending)) return Optional.of(Stale.CROSSED);
        if (context.now() > pending.expiresAt()) return Optional.of(Stale.EXPIRED);
        if (!pending.openKey().equals(TradeLegality.openKey(game, pending.purpose(), deal))) {
            return Optional.of(Stale.WINDOW_CLOSED);
        }
        if (!stillCovered(seat, partner, deal)) return Optional.of(Stale.UNCOVERABLE);
        return Optional.empty();
    }

    private static boolean crossed(AiTurnContext context, Player partner, Pending pending) {
        boolean weSortFirst = context.faction().compareTo(partner.getFaction()) < 0;
        return IncomingOffer.live(context).stream()
                .filter(offer -> offer.isFrom(context.game(), partner))
                .filter(offer -> offer.isCurrent(context.game(), context.seat()))
                .anyMatch(offer -> !AiSeats.isAiSeat(partner)
                        || !weSortFirst
                        || offer.prompt().createdAtMillis() >= pending.sentAt());
    }

    private static boolean stillCovered(Player seat, Player partner, Deal deal) {
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        boolean oursHeld = deal.total(ours, ItemType.COMMODITIES) <= seat.getCommodities()
                && deal.total(ours, ItemType.TRADE_GOODS) <= seat.getTg()
                && deal.total(ours, ItemType.CLEAR_DEBT) <= seat.getDebtTokenCount(partner.getColor())
                && deal.total(ours, ItemType.SEND_DEBT) <= DebtRules.room(seat, partner)
                && deal.notes(ours).stream()
                        .allMatch(detail -> NotesForTrade.aliasOf(seat, detail)
                                .filter(alias -> NotesForTrade.inHand(seat, alias))
                                .isPresent());
        boolean theirsHeld = partner.getTg() >= deal.total(theirs, ItemType.TRADE_GOODS)
                && partner.getCommodities() + partner.getTg() >= deal.total(theirs, ItemType.COMMODITIES);
        return oursHeld && theirsHeld;
    }

    private static Optional<AiDecision> rescind(AiTurnContext context, Player partner, Pending pending, Stale stale) {
        String reason = "trade: rescind the offer to " + partner.getFaction() + " (" + stale.reason + ")";
        Optional<Match> button =
                newestRescind(context, partner, Long.MIN_VALUE).or(() -> rememberedRescind(context, partner, pending));
        if (button.isEmpty() && OfferBuilder.active(context)) return Optional.empty();
        if (ranOutOfTime(partner, stale)) penalise(context, partner, pending);
        if (stale == Stale.CROSSED && pending.purpose().settles()) TradeCardRules.countered(context, partner);
        close(context, partner, sentNumber(context, partner));
        if (button.isPresent()) return Optional.of(button.get().press(reason));
        return OfferBuilder.startCleanup(context, partner);
    }

    private static boolean ranOutOfTime(Player partner, Stale stale) {
        return stale == Stale.EXPIRED || (stale == Stale.WINDOW_CLOSED && !AiSeats.isAiSeat(partner));
    }

    private static void penalise(AiTurnContext context, Player partner, Pending pending) {
        Deal deal = Deal.between(context.seat(), partner);
        if (!TradeValue.theirSideCovered(context.seat(), partner, deal)) return;
        if (pending.purpose().settles()) {
            TradeCardRules.unpaidSettlement(context, partner).ifPresent(event -> Trust.record(context, partner, event));
        } else if (pending.purpose() == Purpose.DEBT_COLLECTION) {
            Trust.record(context, partner, Trust.Event.COLLECTION_UNPAID);
        }
    }

    private static Optional<Match> newestRescind(AiTurnContext context, Player partner, long since) {
        String handlerId = RESCIND + partner.getColor();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> button = prompt.firstEnabled(candidate -> candidate.isUnowned()
                    && isRescind(candidate.handlerId(), handlerId)
                    && !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) return Optional.of(new Match(prompt, button.get()));
        }
        return Optional.empty();
    }

    private static boolean isRescind(String handler, String wanted) {
        return handler.equals(wanted) || handler.startsWith(wanted + BLACK_MARKET);
    }

    private static Optional<Match> rememberedRescind(AiTurnContext context, Player partner, Pending pending) {
        return pending.rememberedRescind(RESCIND + partner.getColor(), context.now())
                .map(prompt -> new Match(prompt, prompt.buttons().getFirst()))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
    }

    private static int sentNumber(AiTurnContext context, Player partner) {
        String stored = context.game().getStoredValue(IncomingOffer.offerNumberKey(context.seat(), partner));
        return TradeMemory.parseInt(stored).orElse(0);
    }

    private static long replyTimeout(Player partner, Purpose purpose) {
        if (AiSeats.isAiSeat(partner)) return AI_REPLY_TIMEOUT_MILLIS;
        return purpose.waitsLongForHumans() ? HUMAN_LONG_REPLY_TIMEOUT_MILLIS : HUMAN_REPLY_TIMEOUT_MILLIS;
    }
}
