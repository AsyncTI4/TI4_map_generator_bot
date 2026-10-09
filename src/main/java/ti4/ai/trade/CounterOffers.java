package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.trade.TradeValue.Valuation;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class CounterOffers {

    private static final double COUNTER_EXTRA = 0.25;
    private static final int MAX_CHANGE = 2;
    private static final int MAX_TOTAL_CHANGE = 3;
    private static final int NOTE_CHANGE = 1;

    private record Move(int cost, UnaryOperator<Deal> apply) {}

    private record Candidate(int cost, Deal deal) {}

    private record Scored(Deal deal, double utility) {}

    static Optional<Deal> nearest(AiTurnContext context, Player partner, Deal incoming) {
        Deal base = base(context, partner, incoming);
        List<Candidate> candidates = new ArrayList<>();
        combine(moves(context, partner, base), 0, base, 0, candidates);
        for (int cost = 0; cost <= MAX_TOTAL_CHANGE; cost++) {
            int level = cost;
            Optional<Deal> best = candidates.stream()
                    .filter(candidate -> candidate.cost() == level)
                    .map(Candidate::deal)
                    .filter(deal -> !deal.isEmpty() && !deal.sameAs(incoming))
                    .map(deal -> scored(context, partner, deal))
                    .flatMap(Optional::stream)
                    .max(Comparator.comparingDouble(Scored::utility))
                    .map(Scored::deal);
            if (best.isPresent()) return best;
        }
        return Optional.empty();
    }

    static Deal base(AiTurnContext context, Player partner, Deal incoming) {
        Player seat = context.seat();
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        Deal deal = withOneOwnNote(
                context,
                partner,
                incoming.withoutUnsupported()
                        .without(item -> item.type() == ItemType.FRAGMENTS)
                        .withoutNotes(theirs));
        deal = clamp(deal, ours, theirs, ItemType.COMMODITIES, TradeBudget.spareCommodities(context, partner));
        deal = clamp(
                deal, ours, theirs, ItemType.TRADE_GOODS, TradeBudget.spareTradeGoods(context.game(), seat, partner));
        deal = clamp(deal, ours, theirs, ItemType.CLEAR_DEBT, seat.getDebtTokenCount(partner.getColor()));
        deal = clamp(deal, ours, theirs, ItemType.SEND_DEBT, DebtRules.room(seat, partner));
        deal = clamp(deal, theirs, ours, ItemType.COMMODITIES, partner.getCommodities());
        deal = clamp(deal, theirs, ours, ItemType.TRADE_GOODS, partner.getTg());
        return clamp(deal, theirs, ours, ItemType.CLEAR_DEBT, partner.getDebtTokenCount(seat.getColor()));
    }

    private static Optional<Scored> scored(AiTurnContext context, Player partner, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (!TradeValue.coverable(game, seat, partner, deal)) return Optional.empty();
        Valuation valuation = TradeValue.of(game, seat, partner, deal, Trust.of(context, partner));
        boolean worth = valuation.clears(TradeValue.margin(partner) + COUNTER_EXTRA) && valuation.partnerGain() >= 0;
        return worth ? Optional.of(new Scored(deal, valuation.utility())) : Optional.empty();
    }

    private static Deal withOneOwnNote(AiTurnContext context, Player partner, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        String ours = seat.getFaction();
        Deal without = deal.withoutNotes(ours);
        return deal.items().stream()
                .filter(item -> item.isFrom(ours) && item.type() == ItemType.PROMISSORY)
                .map(item -> heldNote(game, seat, partner, item))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(alias -> NotesForTrade.giveCost(game, seat, partner, alias, false)))
                .map(alias -> without.with(new DealItem(
                        ours,
                        partner.getFaction(),
                        ItemType.PROMISSORY,
                        String.valueOf(seat.getPromissoryNotes().get(alias)))))
                .orElse(without);
    }

    private static Optional<String> heldNote(Game game, Player seat, Player partner, DealItem item) {
        if (item.isGenericNote()) return NotesForTrade.leastHarmful(game, seat, partner, false);
        return NotesForTrade.aliasOf(seat, item.detail()).filter(alias -> NotesForTrade.inHand(seat, alias));
    }

    private static Deal clamp(Deal deal, String sender, String receiver, ItemType type, int most) {
        int excess = deal.total(sender, type) - Math.max(0, most);
        return excess > 0 ? deal.adjust(sender, receiver, type, -excess) : deal;
    }

    private static List<List<Move>> moves(AiTurnContext context, Player partner, Deal base) {
        String ours = context.faction();
        String theirs = partner.getFaction();
        List<List<Move>> moves = new ArrayList<>();
        moves.add(lower(base, ours, theirs, ItemType.TRADE_GOODS));
        moves.add(lower(base, ours, theirs, ItemType.COMMODITIES));
        moves.add(lower(base, ours, theirs, ItemType.SEND_DEBT));
        moves.add(raise(base, theirs, ours, ItemType.TRADE_GOODS, partner.getTg()));
        moves.add(raise(base, theirs, ours, ItemType.COMMODITIES, partner.getCommodities()));
        if (mayBorrowFrom(context, partner)) {
            moves.add(raise(base, theirs, ours, ItemType.SEND_DEBT, Integer.MAX_VALUE));
        }
        if (!base.notes(ours).isEmpty()) moves.add(List.of(new Move(NOTE_CHANGE, deal -> deal.withoutNotes(ours))));
        return moves;
    }

    private static boolean mayBorrowFrom(AiTurnContext context, Player partner) {
        boolean emptyHanded = partner.getTg() == 0 && partner.getCommodities() == 0;
        boolean trusted = AiSeats.isAiSeat(partner) || Trust.of(context, partner) >= Trust.LENDING;
        return emptyHanded && trusted;
    }

    private static List<Move> lower(Deal base, String sender, String receiver, ItemType type) {
        List<Move> moves = new ArrayList<>();
        for (int step = 1; step <= Math.min(MAX_CHANGE, base.total(sender, type)); step++) {
            int delta = -step;
            moves.add(new Move(step, deal -> deal.adjust(sender, receiver, type, delta)));
        }
        return moves;
    }

    private static List<Move> raise(Deal base, String sender, String receiver, ItemType type, int most) {
        List<Move> moves = new ArrayList<>();
        int room = most - base.total(sender, type);
        for (int step = 1; step <= Math.min(MAX_CHANGE, room); step++) {
            int delta = step;
            moves.add(new Move(step, deal -> deal.adjust(sender, receiver, type, delta)));
        }
        return moves;
    }

    private static void combine(List<List<Move>> moves, int index, Deal deal, int cost, List<Candidate> out) {
        if (index == moves.size()) {
            out.add(new Candidate(cost, deal));
            return;
        }
        combine(moves, index + 1, deal, cost, out);
        for (Move move : moves.get(index)) {
            if (cost + move.cost() <= MAX_TOTAL_CHANGE) {
                combine(moves, index + 1, move.apply().apply(deal), cost + move.cost(), out);
            }
        }
    }
}
