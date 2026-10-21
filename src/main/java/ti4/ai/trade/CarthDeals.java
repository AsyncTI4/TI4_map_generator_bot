package ti4.ai.trade;

import java.util.Comparator;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class CarthDeals {

    private static final String AGENT = "hacanagent";
    private static final String TARGET_KEY = "hacanAgentTarget|";
    private static final String OFFERED_KEY = "hacanAgentOffered|";
    private static final int MIN_MISSING = 3;

    static Optional<Player> bestTarget(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (TradeBudget.freeCommodities(context) < 1 && TradeBudget.freeTradeGoods(game, seat) < 1) {
            return Optional.empty();
        }
        return Standings.others(game, seat).stream()
                .filter(other -> DealItem.tradable(other.getFaction()))
                .filter(other -> missing(other) >= MIN_MISSING)
                .filter(other -> !Stinginess.feedsRival(game, seat, other))
                .filter(other -> TradeLegality.canTransact(game, seat, other))
                .filter(other -> !TradeLegality.windowUsed(context, other))
                .filter(other -> !PendingOffers.with(context, other))
                .max(Comparator.comparingInt(CarthDeals::missing));
    }

    static void remember(AiTurnContext context, Player target) {
        context.memory().put(TARGET_KEY + context.turnKey(), target.getFaction());
    }

    static Optional<Player> remembered(AiTurnContext context) {
        return context.memory()
                .get(TARGET_KEY + context.turnKey())
                .map(faction -> context.game().getPlayerFromColorOrFaction(faction));
    }

    static Optional<AiDecision> followUp(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        String offered = OFFERED_KEY + context.turnKey();
        Optional<Player> target = remembered(context);
        if (target.isEmpty()
                || context.memory().has(offered)
                || !context.isActivePlayer()
                || !TradeLegality.isActionPhase(game)
                || seat.hasUnexhaustedLeader(AGENT)
                || target.get().getCommodities() < target.get().getCommoditiesTotal()
                || OfferBuilder.active(context)) {
            return Optional.empty();
        }
        Optional<Deal> deal = deal(context, target.get());
        if (deal.isEmpty()) {
            context.memory().put(offered, String.valueOf(context.now()));
            return Optional.empty();
        }
        Optional<AiDecision> start = OfferBuilder.start(context, target.get(), deal.get(), Purpose.AGENT_TRADE);
        start.ifPresent(ignored -> context.memory().put(offered, String.valueOf(context.now())));
        return start;
    }

    static boolean accepts(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        String ours = seat.getFaction();
        int sent = deal.total(ours, ItemType.COMMODITIES);
        int back = deal.received(ours, ItemType.TRADE_GOODS) + deal.received(ours, ItemType.COMMODITIES);
        boolean onlyGoods = deal.items().stream()
                .allMatch(item -> item.type() == ItemType.COMMODITIES || item.type() == ItemType.TRADE_GOODS);
        return offerer.hasLeader(AGENT)
                && !offerer.hasUnexhaustedLeader(AGENT)
                && seat.getCommodities() == seat.getCommoditiesTotal()
                && onlyGoods
                && sent >= 1
                && sent <= seat.getCommodities()
                && back == 1
                && !Stinginess.feedsRival(game, seat, offerer);
    }

    private static Optional<Deal> deal(AiTurnContext context, Player target) {
        Player seat = context.seat();
        String theirs = target.getFaction();
        String ours = seat.getFaction();
        Deal ask = Deal.EMPTY.adjust(theirs, ours, ItemType.COMMODITIES, target.getCommodities() - 1);
        if (TradeBudget.freeCommodities(context) >= 1) {
            return Optional.of(ask.adjust(ours, theirs, ItemType.COMMODITIES, 1));
        }
        if (TradeBudget.freeTradeGoods(context.game(), seat) >= 1) {
            return Optional.of(ask.adjust(ours, theirs, ItemType.TRADE_GOODS, 1));
        }
        return Optional.empty();
    }

    private static int missing(Player player) {
        return player.getCommoditiesTotal() - player.getCommodities();
    }
}
