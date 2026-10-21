package ti4.ai.scoring;

import java.util.Optional;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ExpectedTradeGoods {

    public static final int TRADE_PRIMARY = 3;
    private static final String MASTERS_OF_TRADE = "master_of_trade";

    public static int beforeStatus(Game game, Player seat) {
        int primary = holdsUnplayedTrade(game, seat) ? TRADE_PRIMARY : 0;
        return primary + washableCommodities(game, seat);
    }

    public static int ifHoldingTrade(Game game, Player seat) {
        return TRADE_PRIMARY + (canWash(game) ? seat.getCommoditiesTotal() : 0);
    }

    public static int ifFollowingTrade(Game game, Player seat) {
        if (!canWash(game)) return 0;
        boolean canReplenish = seat.getStrategicCC() > 0 || seat.hasAbility(MASTERS_OF_TRADE);
        return canReplenish ? seat.getCommoditiesTotal() : seat.getCommodities();
    }

    private static boolean canWash(Game game) {
        return AiSettings.isTradingEnabled() && game.getRealPlayers().size() >= 2;
    }

    private static int washableCommodities(Game game, Player seat) {
        if (!canWash(game)) return 0;
        if (holdsUnplayedTrade(game, seat) || seat.hasAbility(MASTERS_OF_TRADE)) {
            return Math.max(heldCommodities(seat), seat.getCommoditiesTotal());
        }
        if (tradeStillToBePlayed(game)) return soldToTheTradeHolder(seat.getCommoditiesTotal());
        return hasWashPartner(game, seat) ? soldToTheTradeHolder(heldCommodities(seat)) : 0;
    }

    private static int soldToTheTradeHolder(int commodities) {
        return Math.max(0, commodities - 1);
    }

    private static int heldCommodities(Player seat) {
        return Math.max(0, seat.getCommodities() - PromisedTradeGoods.sent(seat, PromisedTradeGoods.COMMODITIES, null));
    }

    private static boolean hasWashPartner(Game game, Player seat) {
        return game.getRealPlayers().stream().anyMatch(other -> other != seat && other.getCommodities() >= 1);
    }

    private static boolean holdsUnplayedTrade(Game game, Player seat) {
        return seat.getSCs().stream().anyMatch(initiative -> isUnplayedTrade(game, initiative));
    }

    private static boolean tradeStillToBePlayed(Game game) {
        return game.getRealPlayers().stream()
                .flatMap(player -> player.getSCs().stream())
                .anyMatch(initiative -> isUnplayedTrade(game, initiative));
    }

    private static boolean isUnplayedTrade(Game game, int initiative) {
        return StrategyCard.of(game, initiative) == StrategyCard.TRADE
                && !game.getPlayedSCs().contains(initiative);
    }

    public static int tradeGoodsUsed(SpendCost cost, SpendCost net, int tradeGoods, int tradeGoodValue) {
        int covered = cost.tradeGoods()
                - net.tradeGoods()
                + Math.ceilDiv(cost.resources() - net.resources(), tradeGoodValue)
                + Math.ceilDiv(cost.influence() - net.influence(), tradeGoodValue);
        return Math.min(Math.max(0, tradeGoods), covered);
    }

    public static Optional<SpendCost> netOf(
            SpendCost cost, int tradeGoods, int tradeGoodValue, Predicate<SpendCost> planetsCanPay) {
        int expected = Math.max(0, tradeGoods);
        int ownPart = Math.min(expected, cost.tradeGoods());
        int spare = expected - ownPart;
        int resources = cost.resources();
        int influence = cost.influence();
        int keptTradeGoods = cost.tradeGoods() - ownPart;
        for (int used = 0; used <= spare; used++) {
            for (int toResources = 0; toResources <= used; toResources++) {
                SpendCost net = new SpendCost(
                        Math.max(0, resources - toResources * tradeGoodValue),
                        Math.max(0, influence - (used - toResources) * tradeGoodValue),
                        keptTradeGoods,
                        cost.tokens());
                if (planetsCanPay.test(net)) return Optional.of(net);
            }
        }
        return Optional.empty();
    }
}
