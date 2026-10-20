package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class FragmentSecretDeals {

    static final int MAX_PER_ROUND = 2;
    private static final String ROUND_KEY = "tradeFragmentSecretDeals|";
    private static final String PARTNER_KEY = "tradeFragmentSecretDealWith|";
    private static final String KEY_SEPARATOR = "|";

    record Option(Player partner, Deal deal, double utility) {}

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        Player seat = context.seat();
        if (!FragmentSecrets.chases(seat)) return Optional.empty();
        Game game = context.game();
        String roundKey = ROUND_KEY + game.getRound();
        if (TradeMemory.count(context.memory(), roundKey) >= MAX_PER_ROUND) return Optional.empty();
        List<Player> eligible = partners.stream()
                .filter(partner -> OwnTurnDeals.mayApproach(context, partner))
                .filter(partner -> !context.memory().has(partnerKey(game, partner)))
                .filter(partner -> !OfferRejections.dropped(context, partner))
                .toList();
        for (Option option : best(context, eligible)) {
            Optional<AiDecision> start =
                    OfferBuilder.start(context, option.partner(), option.deal(), Purpose.FRAGMENT_DEAL);
            if (start.isEmpty()) continue;
            TradeMemory.increment(context.memory(), roundKey);
            FragmentSecrets.asked(context.memory(), option.partner());
            OfferRejections.noteAsked(context.memory(), option.partner(), OfferRejections.FRAGMENTS_ASKED);
            context.memory().put(partnerKey(game, option.partner()), String.valueOf(context.now()));
            return start;
        }
        return Optional.empty();
    }

    static List<Option> best(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        List<Option> options = new ArrayList<>();
        for (Player partner : partners) {
            double trust = Trust.of(context, partner);
            int floor = FragmentSecrets.askFloor(context.memory(), game, partner);
            int spare = TradeBudget.spareTradeGoods(game, seat, partner);
            for (Deal deal : candidates(seat, partner, spare, floor)) {
                if (!TradeValue.coverable(game, seat, partner, deal)) continue;
                if (!TradeValue.proposable(game, seat, partner, deal, trust)) continue;
                options.add(new Option(
                        partner,
                        deal,
                        TradeValue.of(game, seat, partner, deal, trust).utility()));
            }
        }
        options.sort(Comparator.comparingDouble(Option::utility).reversed());
        return options;
    }

    static List<Deal> candidates(Player seat, Player partner, int spareTradeGoods, int floor) {
        String ours = seat.getFaction();
        String theirs = partner.getFaction();
        int wanted = FragmentSecrets.missing(seat);
        List<Deal> deals = new ArrayList<>();
        for (String kind : ComponentValues.FRAGMENT_KINDS) {
            int offered = Math.min(wanted, ComponentValues.fragmentsOfKind(partner, kind));
            for (int amount = 1; amount <= offered; amount++) {
                DealItem fragments = new DealItem(theirs, ours, ItemType.FRAGMENTS, kind + amount);
                int highest = Math.min(FragmentSecrets.MAX_PRICE, spareTradeGoods / amount);
                for (int price = floor; price <= highest; price++) {
                    deals.add(Deal.EMPTY.with(fragments).adjust(ours, theirs, ItemType.TRADE_GOODS, price * amount));
                }
            }
        }
        return deals;
    }

    private static String partnerKey(Game game, Player partner) {
        return PARTNER_KEY + game.getRound() + KEY_SEPARATOR + partner.getFaction();
    }
}
