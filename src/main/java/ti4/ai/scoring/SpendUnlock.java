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

    public static boolean unlocks(Game game, Player player, Collection<String> readiedPlanets, int extraTradeGoods) {
        Wallet now = Wallet.of(game, player);
        Wallet after = now.withPlanets(game, readiedPlanets).withTradeGoods(now.tradeGoods() + extraTradeGoods);
        return unlockedPoints(game, player, now, after) > 0;
    }

    public static int pointDelta(Game game, Player player, int tradeGoodDelta) {
        if (tradeGoodDelta > 0) return pointsGained(game, player, tradeGoodDelta);
        if (tradeGoodDelta < 0) return -pointsLost(game, player, -tradeGoodDelta);
        return 0;
    }

    private static int pointsGained(Game game, Player player, int tradeGoods) {
        Wallet now = Wallet.of(game, player);
        Wallet after = now.withTradeGoods(now.tradeGoods() + tradeGoods);
        return unlockedPoints(game, player, now, after);
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

    private static int unlockedPoints(Game game, Player player, Wallet now, Wallet after) {
        List<String> unlocked = game.getRevealedPublicObjectives().keySet().stream()
                .filter(objective -> !ObjectivePolicy.hasScored(game, player, objective))
                .filter(objective -> ObjectiveCatalog.spendCost(objective)
                        .filter(cost -> !now.canPay(cost) && after.canPay(cost))
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
}
