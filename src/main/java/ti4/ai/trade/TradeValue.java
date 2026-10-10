package ti4.ai.trade;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TradeValue {

    private static final double AI_MARGIN = 0.3;
    private static final double HUMAN_MARGIN = 0.5;
    private static final double PROPOSE_MIN = 0.5;
    private static final double PREDICTED_MIN = 0.3;
    private static final double HUMAN_GAIN_MIN = 0.5;
    private static final double PARTNER_TRUST_IN_AI = 1.0;

    record Valuation(
            double selfGain, double partnerGain, double rivalry, double leak, double utility, Optional<String> veto) {

        boolean clears(double margin) {
            return veto.isEmpty() && utility >= margin;
        }
    }

    static Valuation of(Game game, Player seat, Player partner, Deal deal, double trust) {
        return evaluate(game, seat, partner, deal, trust, false);
    }

    static double predictedFor(Game game, Player partner, Player seat, Deal deal) {
        Valuation theirs = evaluate(game, partner, seat, deal, PARTNER_TRUST_IN_AI, true);
        return theirs.veto().isPresent() ? Double.NEGATIVE_INFINITY : theirs.utility();
    }

    static double margin(Player partner) {
        return AiSeats.isAiSeat(partner) ? AI_MARGIN : HUMAN_MARGIN;
    }

    static boolean acceptable(Game game, Player seat, Player partner, Deal deal, double trust) {
        return coverable(game, seat, partner, deal)
                && of(game, seat, partner, deal, trust).clears(margin(partner));
    }

    static boolean proposable(Game game, Player seat, Player partner, Deal deal, double trust) {
        Valuation valuation = of(game, seat, partner, deal, trust);
        if (!valuation.clears(PROPOSE_MIN)) return false;
        if (AiSeats.isAiSeat(partner)) return predictedFor(game, partner, seat, deal) >= PREDICTED_MIN;
        return valuation.partnerGain() >= HUMAN_GAIN_MIN;
    }

    static boolean commitmentOk(Game game, Player seat, Player partner, Deal deal) {
        boolean vetoed =
                Stinginess.vetoes(game, seat, partner, deal).stream().anyMatch(veto -> veto != Stinginess.Veto.LEADER);
        return !vetoed && coverable(game, seat, partner, deal);
    }

    static boolean commitmentOk(AiTurnContext context, Player partner, Deal deal) {
        return commitmentOk(context.game(), context.seat(), partner, deal) && ourSideCovered(context, partner, deal);
    }

    static boolean coverable(Game game, Player seat, Player partner, Deal deal) {
        return ourSideCovered(game, seat, partner, deal) && theirSideCovered(seat, partner, deal);
    }

    private static Valuation evaluate(
            Game game, Player self, Player other, Deal deal, double trust, boolean selfPrivate) {
        String faction = self.getFaction();
        boolean desperate = Desperation.tradeGoodsShort(game, self).isPresent();
        double components = 0;
        for (DealItem item : deal.items()) {
            if (item.isTo(faction)) components += ComponentValues.receive(game, self, other, item, trust, selfPrivate);
            if (item.isFrom(faction)) {
                components -= ComponentValues.give(game, self, other, item, trust, desperate, selfPrivate);
            }
        }
        double leak = Stinginess.pillageLeak(game, self, other, deal);
        double selfGain = components + Desperation.term(game, self, deal.netTradeGoodsTo(faction)) - leak;
        double partnerGain = Stinginess.partnerGain(game, other, self, deal, selfPrivate);
        double rivalry = Stinginess.rivalry(game, self, other);
        double utility = selfGain - rivalry * Math.max(0, partnerGain);
        return new Valuation(selfGain, partnerGain, rivalry, leak, utility, Stinginess.veto(game, self, other, deal));
    }

    static boolean ourSideCovered(Game game, Player seat, Player partner, Deal deal) {
        String faction = seat.getFaction();
        return deal.total(faction, ItemType.COMMODITIES) <= TradeBudget.spareCommodities(seat, partner)
                && deal.total(faction, ItemType.TRADE_GOODS) <= TradeBudget.spareTradeGoods(game, seat, partner)
                && deal.total(faction, ItemType.CLEAR_DEBT) <= seat.getDebtTokenCount(partner.getColor())
                && deal.total(faction, ItemType.SEND_DEBT) <= DebtRules.room(seat, partner)
                && fragmentsHeld(seat, deal)
                && notesHeld(game, seat, partner, deal);
    }

    static boolean ourSideCovered(AiTurnContext context, Player partner, Deal deal) {
        return ourSideCovered(context.game(), context.seat(), partner, deal)
                && deal.total(context.faction(), ItemType.COMMODITIES)
                        <= TradeBudget.spareCommodities(context, partner);
    }

    static boolean theirSideCovered(Player seat, Player partner, Deal deal) {
        String faction = partner.getFaction();
        int tradeGoods = deal.total(faction, ItemType.TRADE_GOODS);
        int commodities = deal.total(faction, ItemType.COMMODITIES);
        return partner.getTg() >= tradeGoods
                && partner.getCommodities() + partner.getTg() - tradeGoods >= commodities
                && deal.total(faction, ItemType.CLEAR_DEBT) <= partner.getDebtTokenCount(seat.getColor())
                && fragmentsHeld(partner, deal);
    }

    private static boolean fragmentsHeld(Player sender, Deal deal) {
        String faction = sender.getFaction();
        return ComponentValues.FRAGMENT_KINDS.stream()
                .allMatch(kind -> fragmentsSent(deal, faction, kind) <= ComponentValues.fragmentsOfKind(sender, kind));
    }

    private static int fragmentsSent(Deal deal, String faction, String kind) {
        return deal.items().stream()
                .filter(item -> item.isFrom(faction) && item.fragmentKind().equals(kind))
                .mapToInt(DealItem::amount)
                .sum();
    }

    private static boolean notesHeld(Game game, Player seat, Player partner, Deal deal) {
        String faction = seat.getFaction();
        return deal.items().stream()
                .filter(item -> item.isFrom(faction) && item.type() == ItemType.PROMISSORY)
                .allMatch(item -> item.isGenericNote()
                        ? NotesForTrade.leastHarmful(game, seat, partner, false).isPresent()
                        : NotesForTrade.aliasOf(seat, item.detail())
                                .filter(alias -> NotesForTrade.inHand(seat, alias))
                                .isPresent());
    }
}
