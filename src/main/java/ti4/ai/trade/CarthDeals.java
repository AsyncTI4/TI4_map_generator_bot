package ti4.ai.trade;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class CarthDeals {

    record Plan(Player target, int gain, int received) {}

    private static final String AGENT = "hacanagent";
    private static final String TARGET_KEY = "hacanAgentTarget|";
    private static final String OFFERED_KEY = "hacanAgentOffered|";
    private static final String ASKED_KEY = "hacanAgentAsked|";
    private static final int SMALL_VALUE = 4;
    private static final int MIN_RECEIVED = 2;

    static Optional<Plan> bestPlan(AiTurnContext context) {
        return plans(context)
                .filter(plan -> plan.target().getCommodities() <= plan.gain())
                .max(ranking());
    }

    static Optional<Player> bestTarget(AiTurnContext context) {
        return bestPlan(context).map(Plan::target);
    }

    static int standardGain(int value) {
        return value <= SMALL_VALUE ? 1 : 2;
    }

    static int stingyGain(int value) {
        return Math.max(standardGain(value), value / 2);
    }

    static boolean stingy(Game game, Player target, Player hacan) {
        return Stinginess.feedsRival(game, target, hacan) || Stinginess.leads(game, hacan);
    }

    static int requiredGain(Game game, Player target, Player hacan) {
        int value = target.getCommoditiesTotal();
        return stingy(game, target, hacan) ? stingyGain(value) : standardGain(value);
    }

    static boolean acceptable(Game game, Player target, Player hacan, int gain) {
        return gain >= requiredGain(game, target, hacan);
    }

    static void remember(AiTurnContext context, Player target) {
        context.memory().put(TARGET_KEY + context.turnKey(), target.getFaction());
    }

    static Optional<Player> remembered(AiTurnContext context) {
        return context.memory()
                .get(TARGET_KEY + context.turnKey())
                .map(faction -> context.game().getPlayerFromColorOrFaction(faction));
    }

    static Optional<AiDecision> ask(AiTurnContext context, Player target) {
        String key = ASKED_KEY + context.turnKey();
        if (AiSeats.isAiSeat(target) || context.memory().has(key)) return Optional.empty();
        context.memory().put(key, String.valueOf(context.now()));
        int gain = requiredGain(context.game(), target, context.seat());
        int sent = target.getCommoditiesTotal() - gain;
        String text = "🤖 " + target.getRepresentationNoPing() + ", will you take " + gain
                + " trade goods if I use Carth of Golden Sands on you? You would send me " + sent
                + " of the commodities it gives you and I would send you " + gain + ".";
        return Optional.of(new AiDecision.Announce(text));
    }

    static Optional<AiDecision> preWash(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        Player active = game.getActivePlayer();
        if (active == null
                || context.isActivePlayer()
                || !TradeLegality.isActionPhase(game)
                || !seat.hasUnexhaustedLeader(AGENT)
                || tradeUnplayed(game)
                || OfferBuilder.active(context)) {
            return Optional.empty();
        }
        Optional<Plan> plan =
                plans(context).filter(candidate -> candidate.target() == active).findFirst();
        if (plan.isEmpty()) return Optional.empty();
        int held = active.getCommodities();
        int washed =
                Math.min(held, TradeBudget.freeCommodities(context) - plan.get().gain());
        if (washed < 1
                || held - washed > plan.get().gain()
                || held <= plan.get().gain()) return Optional.empty();
        Deal deal = Deal.EMPTY
                .adjust(active.getFaction(), seat.getFaction(), ItemType.COMMODITIES, washed)
                .adjust(seat.getFaction(), active.getFaction(), ItemType.COMMODITIES, washed);
        Optional<AiDecision> start = OfferBuilder.start(context, active, deal, Purpose.WASH);
        start.ifPresent(ignored -> OwnTurnDeals.noteApproach(context, active));
        return start;
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
        start.ifPresent(ignored -> {
            context.memory().put(offered, String.valueOf(context.now()));
            OwnTurnDeals.noteApproach(context, target.get());
        });
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
                && sent + back <= seat.getCommoditiesTotal()
                && acceptable(game, seat, offerer, back);
    }

    private static Stream<Plan> plans(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        int give = TradeBudget.freeCommodities(context) + TradeBudget.freeTradeGoods(game, seat);
        return Standings.others(game, seat).stream()
                .filter(other -> DealItem.tradable(other.getFaction()))
                .filter(other -> !Stinginess.feedsRival(game, seat, other))
                .filter(other -> TradeLegality.canTransact(game, seat, other))
                .filter(other -> !TradeLegality.windowUsed(context, other))
                .filter(other -> !PendingOffers.with(context, other))
                .filter(other -> AiSeats.isAiSeat(other) || OwnTurnDeals.mayApproach(context, other))
                .map(other -> plan(game, seat, other))
                .filter(plan -> plan.gain() <= give && plan.received() >= MIN_RECEIVED);
    }

    private static Plan plan(Game game, Player seat, Player target) {
        int gain = requiredGain(game, target, seat);
        return new Plan(target, gain, target.getCommoditiesTotal() - gain);
    }

    private static Comparator<Plan> ranking() {
        return Comparator.comparingInt(Plan::received)
                .thenComparing(Comparator.comparingInt(Plan::gain).reversed());
    }

    private static Optional<Deal> deal(AiTurnContext context, Player target) {
        Game game = context.game();
        Player seat = context.seat();
        int gain = requiredGain(game, target, seat);
        int commodities = Math.min(gain, TradeBudget.freeCommodities(context));
        int tradeGoods = gain - commodities;
        if (tradeGoods > TradeBudget.freeTradeGoods(game, seat)) return Optional.empty();
        String theirs = target.getFaction();
        String ours = seat.getFaction();
        return Optional.of(Deal.EMPTY
                .adjust(theirs, ours, ItemType.COMMODITIES, target.getCommodities() - gain)
                .adjust(ours, theirs, ItemType.COMMODITIES, commodities)
                .adjust(ours, theirs, ItemType.TRADE_GOODS, tradeGoods));
    }

    private static boolean tradeUnplayed(Game game) {
        return game.getRealPlayers().stream()
                .flatMap(player -> player.getSCs().stream())
                .anyMatch(initiative -> StrategyCard.of(game, initiative) == StrategyCard.TRADE
                        && !game.getPlayedSCs().contains(initiative));
    }
}
