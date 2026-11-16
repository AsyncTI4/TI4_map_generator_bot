package ti4.ai.trade;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiGameWrites;
import ti4.ai.AiPersonality;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class FriendlyTrade {

    private static final String FLIP_OFFERED_KEY = "friendlyFlipOffered|";
    static final String SNUBS_KEY = "aiBoatFloatSnubs";
    private static final String SNUB_SEPARATOR = ",";
    private static final String SNUB_REASON = "AI BOAT_FLOAT stops floating a player who charged it";
    private static final int FRIENDLY_FEE = 1;

    static boolean applies(Game game, Player seat, Player partner, double trust) {
        return AiPersonality.isBoatFloat(seat)
                && goodRelations(game, seat, partner, trust)
                && !snubbedBy(seat, partner);
    }

    static boolean snubbedBy(Player seat, Player partner) {
        return snubs(seat).contains(partner.getFaction());
    }

    static void observeTradeTerms(Player seat, Player holder, Optional<Integer> fee, Optional<String> exclusion) {
        if (!AiPersonality.isBoatFloat(seat)) return;
        boolean fairExclusion = exclusion.filter(FriendlyTrade::fairExclusion).isPresent();
        if (fee.isEmpty() && fairExclusion) return;
        if (fee.isEmpty() || fee.get() > friendlyFee(seat.getCommoditiesTotal())) snub(seat, holder);
    }

    static void observeOffer(AiTurnContext context, Player offerer, Deal deal) {
        Player seat = context.seat();
        if (!AiPersonality.isBoatFloat(seat)) return;
        int charged = chargedForOurCommodities(seat, offerer, deal);
        if (charged < 1) return;
        int ourCommodities = deal.total(seat.getFaction(), ItemType.COMMODITIES);
        boolean tradeSettlement = TradeCardRules.followsTradeOf(context.game(), seat, offerer);
        int allowed = tradeSettlement ? friendlyFee(Math.max(ourCommodities, seat.getCommoditiesTotal())) : 0;
        if (charged > allowed) snub(seat, offerer);
    }

    static boolean goodRelations(Game game, Player seat, Player partner, double trust) {
        return trust >= Trust.LOWER_FEE && Stinginess.grudgeTenths(game, seat, partner) == 0;
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
        if (!AiPersonality.isBoatFloat(seat) || TradeBudget.freeCommodities(context) > 0) return Optional.empty();
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

    private static boolean fairExclusion(String reason) {
        return TradeTerms.isWinExclusion(reason);
    }

    private static int friendlyFee(int stack) {
        return Math.max(0, Math.min(FRIENDLY_FEE, stack - 1));
    }

    private static int chargedForOurCommodities(Player seat, Player partner, Deal deal) {
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        boolean onlyTradeGoodsIn = deal.items().stream()
                .filter(item -> item.isFrom(theirs))
                .allMatch(item -> item.type() == ItemType.TRADE_GOODS);
        if (!onlyTradeGoodsIn) return 0;
        boolean onlyCommoditiesOut = deal.items().stream()
                .filter(item -> item.isFrom(ours))
                .allMatch(item -> item.type() == ItemType.COMMODITIES);
        int tradeGoodsIn = deal.total(theirs, ItemType.TRADE_GOODS);
        if (onlyCommoditiesOut) return deal.total(ours, ItemType.COMMODITIES) - tradeGoodsIn;
        boolean onlyFeeOut = tradeGoodsIn == 0
                && deal.items().stream()
                        .filter(item -> item.isFrom(ours))
                        .allMatch(item -> item.type() == ItemType.TRADE_GOODS || item.type() == ItemType.SEND_DEBT);
        if (!onlyFeeOut || !TradeCardRules.followsTradeOf(seat.getGame(), seat, partner)) return 0;
        return deal.total(ours, ItemType.TRADE_GOODS) + deal.total(ours, ItemType.SEND_DEBT);
    }

    private static List<String> snubs(Player seat) {
        return Arrays.stream(seat.getStoredValue(SNUBS_KEY).split(SNUB_SEPARATOR))
                .map(String::trim)
                .filter(faction -> !faction.isEmpty())
                .toList();
    }

    private static void snub(Player seat, Player partner) {
        if (snubbedBy(seat, partner)) return;
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        AiGameWrites.persist(seat.getGame(), SNUB_REASON, latest -> {
            Player recorder = latest.getPlayerFromColorOrFaction(ours);
            if (recorder == null || snubs(recorder).contains(theirs)) return;
            String stored = recorder.getStoredValue(SNUBS_KEY);
            recorder.setStoredValue(SNUBS_KEY, stored.isBlank() ? theirs : stored + SNUB_SEPARATOR + theirs);
        });
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
