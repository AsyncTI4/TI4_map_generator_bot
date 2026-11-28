package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.Hostility;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class FriendlyTrade {

    private static final String FLIP_OFFERED_KEY = "friendlyFlipOffered|";
    private static final int FRIENDLY_FEE = 1;
    private static final String HOSTILE_TERMS_KEY = "hostileTerms|";
    private static final String HOSTILE_OFFER_KEY = "hostileOffer|";

    static boolean applies(Game game, Player seat, Player partner, double trust) {
        return Relations.good(game, seat, partner, trust);
    }

    static void observeTradeTerms(
            AiTurnContext context, Player holder, Optional<Integer> fee, Optional<String> exclusion, int standardFee) {
        Player seat = context.seat();
        chargeForTerms(context, holder, fee, exclusion);
        if (fee.isPresent() && fee.get() < standardFee) {
            Relations.improve(context, holder);
            return;
        }
        if (!applies(context.game(), seat, holder, Trust.of(context, holder))) return;
        boolean fairExclusion = exclusion.filter(TradeTerms::isWinExclusion).isPresent();
        if (fee.isEmpty() && fairExclusion) return;
        if (fee.isEmpty() || fee.get() > friendlyFee(seat.getCommoditiesTotal())) Relations.worsen(context, holder);
    }

    static void observeOffer(AiTurnContext context, Player offerer, Deal deal) {
        Player seat = context.seat();
        OptionalInt charged = chargedForOurCommodities(seat, offerer, deal);
        if (charged.isEmpty()) return;
        if (charged.getAsInt() <= 0) {
            Relations.improve(context, offerer);
            return;
        }
        int ourCommodities = deal.total(seat.getFaction(), ItemType.COMMODITIES);
        boolean tradeSettlement = TradeCardRules.followsTradeOf(context.game(), seat, offerer);
        int allowed = tradeSettlement ? friendlyFee(Math.max(ourCommodities, seat.getCommoditiesTotal())) : 0;
        int excess = charged.getAsInt() - allowed;
        if (excess <= 0) return;
        boolean termsCounted = tradeSettlement && AiSeats.isAiSeat(offerer);
        if (!termsCounted) chargeOnce(context, offerer, HOSTILE_OFFER_KEY + deal.fingerprint(), excess);
        if (applies(context.game(), seat, offerer, Trust.of(context, offerer))) Relations.worsen(context, offerer);
    }

    private static void chargeForTerms(
            AiTurnContext context, Player holder, Optional<Integer> fee, Optional<String> exclusion) {
        int friendly = friendlyFee(context.seat().getCommoditiesTotal());
        boolean fairExclusion = exclusion.filter(TradeTerms::isWinExclusion).isPresent();
        double lost = fee.map(charged -> (double) Math.max(0, charged - friendly))
                .orElse(fairExclusion ? 0.0 : context.seat().getCommoditiesTotal() - friendly);
        chargeOnce(context, holder, HOSTILE_TERMS_KEY + context.game().getRound(), lost);
    }

    private static void chargeOnce(AiTurnContext context, Player offender, String event, double tradeGoods) {
        if (tradeGoods <= 0) return;
        String key = event + "|" + offender.getFaction();
        if (context.memory().has(key)) return;
        context.memory().put(key, "1");
        Hostility.charge(context.game(), offender, context.seat(), tradeGoods);
    }

    static boolean couldWinWith(Game game, Player player, int tradeGoods) {
        int points = SpendUnlock.pointDelta(game, player, tradeGoods);
        return points > 0 && Standings.vp(player) + points >= Standings.goal(game);
    }

    static boolean acceptsFlip(AiTurnContext context, Player partner, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (!applies(game, seat, partner, Trust.of(context, partner))) return false;
        int commodities = flippedCommodities(seat, partner, deal);
        int tradeGoods = deal.total(seat.getFaction(), ItemType.TRADE_GOODS);
        if (commodities < 1 || tradeGoods > commodities) return false;
        if (couldWinWith(game, partner, tradeGoods)) return false;
        return TradeValue.commitmentOk(context, partner, deal);
    }

    static Optional<AiDecision> offerFlip(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        if (TradeBudget.freeCommodities(context) > 0) return Optional.empty();
        String ours = seat.getFaction();
        return partners.stream()
                .filter(partner -> partner.getCommodities() >= 1)
                .filter(partner -> !context.memory().has(flipKey(game, partner)))
                .filter(partner -> OwnTurnDeals.mayApproach(context, partner))
                .filter(partner -> applies(game, seat, partner, Trust.of(context, partner)))
                .map(partner -> {
                    int flipped = Math.min(partner.getCommodities(), TradeBudget.spareTradeGoods(game, seat, partner));
                    return new Flip(
                            partner,
                            Deal.EMPTY
                                    .adjust(partner.getFaction(), ours, ItemType.COMMODITIES, flipped)
                                    .adjust(ours, partner.getFaction(), ItemType.TRADE_GOODS, flipped),
                            flipped);
                })
                .filter(flip -> flip.amount() >= 1)
                .filter(flip -> !couldWinWith(game, flip.partner(), flip.amount()))
                .filter(flip -> TradeValue.coverable(game, seat, flip.partner(), flip.deal()))
                .max(Comparator.comparingInt(Flip::amount))
                .flatMap(flip -> {
                    context.memory().put(flipKey(game, flip.partner()), "1");
                    return OfferBuilder.start(context, flip.partner(), flip.deal(), Purpose.WASH);
                });
    }

    static boolean chargesOneLess(Game game, Player holder, Player follower, double trust) {
        return applies(game, holder, follower, trust);
    }

    private static int friendlyFee(int stack) {
        return Math.max(0, Math.min(FRIENDLY_FEE, stack - 1));
    }

    private static OptionalInt chargedForOurCommodities(Player seat, Player partner, Deal deal) {
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        boolean onlyTradeGoodsIn = deal.items().stream()
                .filter(item -> item.isFrom(theirs))
                .allMatch(item -> item.type() == ItemType.TRADE_GOODS);
        if (!onlyTradeGoodsIn) return OptionalInt.empty();
        boolean onlyCommoditiesOut = deal.items().stream()
                .filter(item -> item.isFrom(ours))
                .allMatch(item -> item.type() == ItemType.COMMODITIES);
        int tradeGoodsIn = deal.total(theirs, ItemType.TRADE_GOODS);
        int commoditiesOut = deal.total(ours, ItemType.COMMODITIES);
        if (onlyCommoditiesOut && commoditiesOut >= 1) return OptionalInt.of(commoditiesOut - tradeGoodsIn);
        boolean onlyFeeOut = tradeGoodsIn == 0
                && deal.items().stream()
                        .filter(item -> item.isFrom(ours))
                        .allMatch(item -> item.type() == ItemType.TRADE_GOODS || item.type() == ItemType.SEND_DEBT);
        int feeOut = deal.total(ours, ItemType.TRADE_GOODS) + deal.total(ours, ItemType.SEND_DEBT);
        if (!onlyFeeOut || feeOut < 1 || !TradeCardRules.followsTradeOf(seat.getGame(), seat, partner)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(feeOut);
    }

    private record Flip(Player partner, Deal deal, int amount) {}

    private static int flippedCommodities(Player seat, Player partner, Deal deal) {
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        boolean onlyCommoditiesIn = deal.items().stream()
                .filter(item -> item.isFrom(theirs))
                .allMatch(item -> item.type() == ItemType.COMMODITIES);
        boolean onlyTradeGoodsOut = deal.items().stream()
                .filter(item -> item.isFrom(ours))
                .allMatch(item -> item.type() == ItemType.TRADE_GOODS);
        if (!onlyCommoditiesIn || !onlyTradeGoodsOut) return 0;
        return deal.total(theirs, ItemType.COMMODITIES);
    }

    private static String flipKey(Game game, Player partner) {
        return FLIP_OFFERED_KEY + game.getRound() + "|" + partner.getFaction();
    }
}
