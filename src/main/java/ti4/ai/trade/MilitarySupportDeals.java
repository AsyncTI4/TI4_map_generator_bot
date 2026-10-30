package ti4.ai.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.SearchMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.turn.EndTurnService;

@UtilityClass
class MilitarySupportDeals {

    private static final String ROUND_KEY = "tradeMilitarySupport|";
    private static final String KEY_SEPARATOR = "|";

    private record Offer(Player partner, Deal deal, double score) {}

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        try (SearchMemo.Open ignored = SearchMemo.open()) {
            return buy(context, partners).or(() -> sell(context, partners));
        }
    }

    static Optional<AiDecision> buy(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        if (MilitarySupport.holdsForeign(seat)
                || seat.isPassed()
                || !MilitarySupport.wantsInfantry(game, seat)
                || TradeBudget.freeTradeGoods(game, seat) < MilitarySupport.PRICE) {
            return Optional.empty();
        }
        Player next = EndTurnService.findNextUnpassedPlayer(game, seat);
        for (Player owner : partners) {
            if (!Standings.same(owner, next)
                    || !MilitarySupport.ownerHolds(owner)
                    || !MilitarySupport.freeToLend(owner)) continue;
            Deal deal = Deal.EMPTY
                    .with(new DealItem(
                            owner.getFaction(), seat.getFaction(), ItemType.PROMISSORY, MilitarySupport.ALIAS))
                    .adjust(seat.getFaction(), owner.getFaction(), ItemType.TRADE_GOODS, MilitarySupport.PRICE);
            Optional<AiDecision> start = start(context, owner, deal);
            if (start.isPresent()) return start;
        }
        return Optional.empty();
    }

    static Optional<AiDecision> sell(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        if (!MilitarySupport.ownerHolds(seat)
                || !MilitarySupport.freeToLend(seat)
                || TradeCardRules.holdsUnplayedTrade(game, seat)) {
            return Optional.empty();
        }
        Integer id = seat.getPromissoryNotes().get(MilitarySupport.ALIAS);
        if (id == null) return Optional.empty();
        List<Offer> offers = partners.stream()
                .filter(partner -> partner.getTg() >= MilitarySupport.PRICE)
                .filter(partner -> !MilitarySupport.holdsForeign(partner))
                .filter(partner -> MilitarySupport.wantsInfantry(game, partner))
                .filter(partner -> !MilitarySupport.publicPathToWin(game, partner))
                .map(partner -> new Offer(
                        partner,
                        Deal.EMPTY
                                .with(new DealItem(
                                        seat.getFaction(),
                                        partner.getFaction(),
                                        ItemType.PROMISSORY,
                                        String.valueOf(id)))
                                .adjust(
                                        partner.getFaction(),
                                        seat.getFaction(),
                                        ItemType.TRADE_GOODS,
                                        MilitarySupport.PRICE),
                        MilitarySupport.receivedBy(game, partner)
                                * (1 - Stinginess.rivalry(game, seat, partner))
                                / (1 + MilitarySupport.lead(game, seat, partner))))
                .sorted(Comparator.comparingDouble(Offer::score).reversed())
                .toList();
        for (Offer offer : offers) {
            Optional<AiDecision> start = start(context, offer.partner(), offer.deal());
            if (start.isPresent()) return start;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> start(AiTurnContext context, Player partner, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        String key = ROUND_KEY + game.getRound() + KEY_SEPARATOR + partner.getFaction();
        if (context.memory().has(key) || !OwnTurnDeals.mayApproach(context, partner)) return Optional.empty();
        if (!TradeValue.coverable(game, seat, partner, deal)
                || !TradeValue.proposable(game, seat, partner, deal, Trust.of(context, partner))) {
            return Optional.empty();
        }
        Optional<AiDecision> start = OfferBuilder.start(context, partner, deal, Purpose.NOTE_DEAL);
        start.ifPresent(ignored -> context.memory().put(key, String.valueOf(context.now())));
        return start;
    }
}
