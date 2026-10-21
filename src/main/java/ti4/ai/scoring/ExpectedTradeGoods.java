package ti4.ai.scoring;

import java.util.stream.Stream;
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
        return primary + evenWash(game, seat);
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

    private static int evenWash(Game game, Player seat) {
        if (!canWash(game) || !seat.hasAbility(MASTERS_OF_TRADE)) return 0;
        int held = Math.max(
                0, seat.getCommodities() - PromisedTradeGoods.sent(seat, PromisedTradeGoods.COMMODITIES, null));
        if (holdsUnplayedTrade(game, seat)) return 0;
        int partner = unplayedTradeHolders(game, seat)
                .mapToInt(Player::getCommoditiesTotal)
                .max()
                .orElse(0);
        return partner == 0 ? held : Math.min(Math.max(held, seat.getCommoditiesTotal()), partner);
    }

    private static Stream<Player> unplayedTradeHolders(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(player -> player != seat)
                .filter(player -> player.getSCs().stream().anyMatch(initiative -> isUnplayedTrade(game, initiative)));
    }

    private static boolean holdsUnplayedTrade(Game game, Player seat) {
        return seat.getSCs().stream().anyMatch(initiative -> isUnplayedTrade(game, initiative));
    }

    private static boolean isUnplayedTrade(Game game, int initiative) {
        return StrategyCard.of(game, initiative) == StrategyCard.TRADE
                && !game.getPlayedSCs().contains(initiative);
    }

    public static int tradeGoodsUsed(SpendCost cost, int tradeGoods, int tradeGoodValue) {
        return tradeGoodsUsed(cost, netOf(cost, tradeGoods, tradeGoodValue), tradeGoods, tradeGoodValue);
    }

    public static int tradeGoodsUsed(SpendCost cost, SpendCost net, int tradeGoods, int tradeGoodValue) {
        int covered = cost.tradeGoods()
                - net.tradeGoods()
                + Math.ceilDiv(cost.resources() - net.resources(), tradeGoodValue)
                + Math.ceilDiv(cost.influence() - net.influence(), tradeGoodValue);
        return Math.min(Math.max(0, tradeGoods), covered);
    }

    public static SpendCost netOfInfluenceFirst(SpendCost cost, int tradeGoods, int tradeGoodValue) {
        int left = Math.max(0, tradeGoods);
        int keptTradeGoods = Math.max(0, cost.tradeGoods() - left);
        left -= cost.tradeGoods() - keptTradeGoods;
        int influence = Math.max(0, cost.influence() - left * tradeGoodValue);
        left -= Math.ceilDiv(cost.influence() - influence, tradeGoodValue);
        int resources = Math.max(0, cost.resources() - left * tradeGoodValue);
        return new SpendCost(resources, influence, keptTradeGoods, cost.tokens());
    }

    public static SpendCost netOf(SpendCost cost, int tradeGoods, int tradeGoodValue) {
        int left = Math.max(0, tradeGoods);
        int keptTradeGoods = Math.max(0, cost.tradeGoods() - left);
        left -= cost.tradeGoods() - keptTradeGoods;
        int resources = Math.max(0, cost.resources() - left * tradeGoodValue);
        left -= Math.ceilDiv(cost.resources() - resources, tradeGoodValue);
        int influence = Math.max(0, cost.influence() - left * tradeGoodValue);
        return new SpendCost(resources, influence, keptTradeGoods, cost.tokens());
    }
}
