package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiPersonality;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class FriendlyTrade {

    private static final String FLIP_OFFERED_KEY = "friendlyFlipOffered|";

    static boolean applies(Game game, Player seat, Player partner, double trust) {
        return AiPersonality.isBoatFloat(seat) && goodRelations(game, seat, partner, trust);
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
