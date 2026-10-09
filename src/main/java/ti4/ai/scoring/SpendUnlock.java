package ti4.ai.scoring;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class SpendUnlock {

    private static final String MIRROR_COMPUTING = "mc";
    private static final int MIRROR_COMPUTING_RATE = 2;

    public static boolean unlocks(Game game, Player player, Collection<String> readiedPlanets, int extraTradeGoods) {
        Wallet now = Wallet.of(game, player);
        Wallet after = now.withPlanets(game, readiedPlanets).withTradeGoods(now.tradeGoods() + extraTradeGoods);
        return unlockedPoints(game, player, now, after, false) > 0;
    }

    public static int pointDelta(Game game, Player player, int tradeGoodDelta) {
        if (tradeGoodDelta > 0) return pointsGained(game, player, tradeGoodDelta);
        if (tradeGoodDelta < 0) return -pointsLost(game, player, -tradeGoodDelta);
        return 0;
    }

    private static int pointsGained(Game game, Player player, int tradeGoods) {
        Wallet now = Wallet.of(game, player);
        Wallet after = now.withTradeGoods(now.tradeGoods() + tradeGoods);
        return unlockedPoints(game, player, now, after, player.hasTech(MIRROR_COMPUTING));
    }

    private static int pointsLost(Game game, Player player, int tradeGoods) {
        Optional<ScoringReserve.Reserved> reserved = ScoringReserve.reserved(game, player);
        if (reserved.isEmpty()) return 0;
        Wallet now = Wallet.of(game, player);
        Wallet after = now.withTradeGoods(now.tradeGoods() - tradeGoods);
        return after.canPay(reserved.get().cost())
                ? 0
                : ObjectivePolicy.victoryPoints(reserved.get().objectiveId());
    }

    private static int unlockedPoints(Game game, Player player, Wallet now, Wallet after, boolean mirrorComputing) {
        List<String> unlocked = game.getRevealedPublicObjectives().keySet().stream()
                .filter(objective -> !ObjectivePolicy.hasScored(game, player, objective))
                .filter(objective -> ObjectiveCatalog.spendCost(objective)
                        .filter(cost -> !canPay(now, cost, mirrorComputing) && canPay(after, cost, mirrorComputing))
                        .isPresent())
                .toList();
        if (unlocked.isEmpty() || !Helper.canPlayerScorePOs(game, player)) return 0;
        List<String> scorableNow = ObjectivePolicy.scorablePublics(game, player);
        int chances = ScoringReserve.scoringChances(game, player);
        int best = 0;
        for (String objective : unlocked) {
            int points = ObjectivePolicy.victoryPoints(objective);
            long asGood = scorableNow.stream()
                    .filter(other -> ObjectivePolicy.victoryPoints(other) >= points)
                    .count();
            if (asGood < chances) best = Math.max(best, points);
        }
        return best;
    }

    private static boolean canPay(Wallet wallet, SpendCost cost, boolean mirrorComputing) {
        if (!mirrorComputing || wallet.tradeGoods() < cost.tradeGoods()) return wallet.canPay(cost);
        int spentAsTradeGoods = cost.tradeGoods();
        int spentAsValue = MIRROR_COMPUTING_RATE * (wallet.tradeGoods() - spentAsTradeGoods);
        return wallet.withTradeGoods(spentAsTradeGoods + spentAsValue).canPay(cost);
    }
}
