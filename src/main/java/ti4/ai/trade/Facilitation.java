package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class Facilitation {

    private static final String FEE_KEY = "tradeFacilitatedFee|";
    private static final String FORWARDED_KEY = "tradeFacilitatedSent|";
    private static final String OFFERED_KEY = "tradeFacilitated|";
    private static final String KEY_SEPARATOR = "|";
    private static final int MAX_FEE = 2;

    static boolean offers(Game game, Player holder, Player hacan) {
        return TradeTerms.mastersOfTrade(hacan)
                && AiSeats.isAiSeat(hacan)
                && AiSeats.isAiSeat(holder)
                && !Standings.same(hacan, holder)
                && !Stinginess.feedsRival(game, holder, hacan);
    }

    static Optional<AiDecision> start(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player hacan = context.seat();
        Optional<Player> holder = TradeCardRules.tradeHolder(game, hacan);
        if (holder.isEmpty() || !offers(game, holder.get(), hacan)) return Optional.empty();
        return washes(context, partners, holder.get()).or(() -> forward(context, holder.get()));
    }

    static boolean accepts(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Player> holder = TradeCardRules.tradeHolder(game, seat);
        if (holder.isEmpty() || !offers(game, holder.get(), offerer) || TradeCardRules.paid(game, seat)) return false;
        if (TradeLegality.neighbours(game, seat, holder.get())) return false;
        boolean onlyCommodities = deal.items().stream().allMatch(item -> item.type() == ItemType.COMMODITIES);
        int sent = deal.total(seat.getFaction(), ItemType.COMMODITIES);
        int back = deal.total(offerer.getFaction(), ItemType.COMMODITIES);
        Optional<Integer> fee = TradeTerms.feeFor(game, holder.get(), seat, Trust.of(context, seat));
        int kept = sent - back;
        return onlyCommodities
                && fee.isPresent()
                && back >= 1
                && kept >= fee.get()
                && kept <= MAX_FEE
                && sent <= seat.getCommodities();
    }

    private static Optional<AiDecision> washes(AiTurnContext context, List<Player> partners, Player holder) {
        Game game = context.game();
        Player hacan = context.seat();
        int free = TradeBudget.freeCommodities(context);
        if (free < 1 || OfferBuilder.active(context)) return Optional.empty();
        int card = TradeCardRules.playedTrade(game, holder).orElseThrow();
        List<Player> owing = TradeCardRules.followers(game, card, holder).stream()
                .filter(follower -> !Standings.same(follower, hacan))
                .filter(follower -> partners.stream().anyMatch(partner -> Standings.same(partner, follower)))
                .filter(follower -> !TradeCardRules.paid(game, follower))
                .filter(follower -> !TradeLegality.neighbours(game, follower, holder))
                .filter(follower -> follower.getCommodities() >= 1)
                .filter(follower -> !Stinginess.feedsRival(game, hacan, follower))
                .filter(follower -> !context.memory().has(offeredKey(game, follower)))
                .sorted(Comparator.comparingDouble(follower -> Stinginess.rivalry(game, hacan, follower)))
                .toList();
        for (Player follower : owing) {
            Optional<Integer> fee = TradeTerms.feeFor(game, holder, follower, Trust.of(context, follower));
            if (fee.isEmpty()) continue;
            int sent = Math.min(follower.getCommodities(), free + fee.get());
            int back = sent - fee.get();
            if (back < 1) continue;
            String theirs = follower.getFaction();
            String ours = hacan.getFaction();
            Deal deal = Deal.EMPTY
                    .adjust(theirs, ours, ItemType.COMMODITIES, sent)
                    .adjust(ours, theirs, ItemType.COMMODITIES, back);
            Optional<AiDecision> start = OfferBuilder.start(context, follower, deal, Purpose.FACILITATED_WASH);
            if (start.isPresent()) {
                context.memory().put(offeredKey(game, follower), String.valueOf(context.now()));
                context.memory().put(feeKey(game, follower), String.valueOf(fee.get()));
                return start;
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> forward(AiTurnContext context, Player holder) {
        Game game = context.game();
        Player hacan = context.seat();
        boolean washesOpen = PendingOffers.count(context, purpose -> purpose == Purpose.FACILITATED_WASH) > 0;
        if (washesOpen
                || OfferBuilder.active(context)
                || TradeLegality.windowUsed(context, holder)
                || PendingOffers.with(context, holder)
                || IncomingOffer.liveFrom(context, holder)) {
            return Optional.empty();
        }
        int card = TradeCardRules.playedTrade(game, holder).orElseThrow();
        List<Player> unforwarded = TradeCardRules.followers(game, card, holder).stream()
                .filter(follower -> hacan.getFaction().equals(TradeCardRules.paidTo(game, follower)))
                .filter(follower -> context.memory().has(feeKey(game, follower)))
                .filter(follower -> !context.memory().has(forwardedKey(game, follower)))
                .toList();
        int owed = unforwarded.stream()
                .mapToInt(follower -> fee(context, follower))
                .sum();
        if (owed < 1) return Optional.empty();
        int tradeGoods = Math.min(owed, TradeBudget.freeTradeGoods(game, hacan));
        int debt = Math.min(owed - tradeGoods, DebtRules.room(hacan, holder));
        String ours = hacan.getFaction();
        String theirs = holder.getFaction();
        Deal deal = Deal.EMPTY
                .adjust(ours, theirs, ItemType.TRADE_GOODS, tradeGoods)
                .adjust(ours, theirs, ItemType.SEND_DEBT, debt);
        if (deal.isEmpty()) return Optional.empty();
        Optional<AiDecision> start = OfferBuilder.start(context, holder, deal, Purpose.FACILITATION_SETTLE);
        start.ifPresent(ignored -> unforwarded.forEach(
                follower -> context.memory().put(forwardedKey(game, follower), String.valueOf(context.now()))));
        return start;
    }

    private static int fee(AiTurnContext context, Player follower) {
        String stored = context.memory().get(feeKey(context.game(), follower)).orElse("0");
        return TradeMemory.parseInt(stored).orElse(0);
    }

    private static String feeKey(Game game, Player follower) {
        return FEE_KEY + game.getRound() + KEY_SEPARATOR + follower.getFaction();
    }

    private static String forwardedKey(Game game, Player follower) {
        return FORWARDED_KEY + game.getRound() + KEY_SEPARATOR + follower.getFaction();
    }

    private static String offeredKey(Game game, Player follower) {
        return OFFERED_KEY + game.getRound() + KEY_SEPARATOR + follower.getFaction();
    }
}
