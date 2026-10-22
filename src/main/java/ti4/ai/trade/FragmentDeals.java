package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.SearchMemo;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class FragmentDeals {

    static final int MAX_PER_ROUND = 2;
    static final int MAX_PRICE = 4;
    private static final String ROUND_KEY = "tradeFragmentDeals|";
    private static final String PARTNER_KEY = "tradeFragmentDealWith|";
    private static final String KEY_SEPARATOR = "|";
    private static final int ONE = 1;

    record Option(Player partner, Deal deal, double utility) {}

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        String roundKey = ROUND_KEY + game.getRound();
        if (TradeMemory.count(context.memory(), roundKey) >= MAX_PER_ROUND) return Optional.empty();
        List<Player> eligible = partners.stream()
                .filter(partner -> OwnTurnDeals.mayApproach(context, partner))
                .filter(partner -> !context.memory().has(partnerKey(game, partner)))
                .toList();
        for (Option option : best(context, eligible)) {
            Optional<AiDecision> start =
                    OfferBuilder.start(context, option.partner(), option.deal(), Purpose.FRAGMENT_DEAL);
            if (start.isEmpty()) continue;
            TradeMemory.increment(context.memory(), roundKey);
            context.memory().put(partnerKey(game, option.partner()), String.valueOf(context.now()));
            return start;
        }
        return Optional.empty();
    }

    static List<Option> best(AiTurnContext context, List<Player> partners) {
        try (SearchMemo.Open ignored = SearchMemo.open()) {
            return rank(context, partners);
        }
    }

    private static List<Option> rank(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        List<Option> options = new ArrayList<>();
        for (Player partner : partners) {
            double trust = Trust.of(context, partner);
            for (Deal deal : candidates(game, seat, partner, TradeBudget.spareTradeGoods(game, seat, partner))) {
                if (!TradeValue.coverable(game, seat, partner, deal)) continue;
                TradeValue.proposal(game, seat, partner, deal, trust)
                        .ifPresent(valuation -> options.add(new Option(partner, deal, valuation.utility())));
            }
        }
        options.sort(Comparator.comparingDouble(Option::utility).reversed());
        return options;
    }

    static List<Deal> candidates(Game game, Player seat, Player partner, int spareTradeGoods) {
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        List<DealItem> given = fragments(seat, ours, theirs);
        List<DealItem> asked = fragments(partner, theirs, ours);
        List<Deal> deals = new ArrayList<>();
        for (DealItem fragment : given) {
            for (int price = 1; price <= Math.min(MAX_PRICE, partner.getTg()); price++) {
                deals.add(Deal.EMPTY.with(fragment).adjust(theirs, ours, ItemType.TRADE_GOODS, price));
            }
        }
        for (DealItem fragment : asked) {
            for (int price = 1; price <= Math.min(MAX_PRICE, spareTradeGoods); price++) {
                deals.add(Deal.EMPTY.with(fragment).adjust(ours, theirs, ItemType.TRADE_GOODS, price));
            }
        }
        for (DealItem mine : given) {
            for (DealItem yours : asked) {
                if (mine.fragmentKind().equals(yours.fragmentKind())) continue;
                Deal swap = Deal.EMPTY.with(mine).with(yours);
                deals.add(swap);
                if (partner.getTg() >= ONE) deals.add(swap.adjust(theirs, ours, ItemType.TRADE_GOODS, ONE));
                if (spareTradeGoods >= ONE) deals.add(swap.adjust(ours, theirs, ItemType.TRADE_GOODS, ONE));
            }
        }
        return deals;
    }

    private static List<DealItem> fragments(Player holder, String sender, String receiver) {
        return FragmentValue.KINDS.stream()
                .filter(kind -> FragmentValue.Holdings.of(holder).of(kind) > 0)
                .map(kind -> new DealItem(sender, receiver, ItemType.FRAGMENTS, kind + ONE))
                .toList();
    }

    private static String partnerKey(Game game, Player partner) {
        return PARTNER_KEY + game.getRound() + KEY_SEPARATOR + partner.getFaction();
    }
}
