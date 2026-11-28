package ti4.ai.scoring;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.SearchMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class SpendUnlock {

    private static final String POINT_DELTA_MEMO = "spend.pointDelta";

    public static boolean unlocks(Game game, Player player, Collection<String> readiedPlanets, int extraTradeGoods) {
        return pointsUnlocked(game, player, readiedPlanets, extraTradeGoods) > 0;
    }

    public static int pointsUnlocked(Game game, Player player, Collection<String> readiedPlanets, int extraTradeGoods) {
        Wallet now = Wallet.of(game, player);
        Wallet after = now.withPlanets(game, readiedPlanets).withTradeGoods(now.tradeGoods() + extraTradeGoods);
        return unlockedPoints(game, player, now, after);
    }

    public static int pointsUnlockedByTokens(Game game, Player player, int tokens) {
        Wallet now = Wallet.of(game, player);
        Wallet after = new Wallet(
                now.planets(),
                now.tradeGoods(),
                now.tacticTokens() + tokens,
                now.strategyTokens(),
                now.tradeGoodValue(),
                now.pooling());
        return unlockedPoints(game, player, now, after);
    }

    public static int pointsLostByExhausting(Game game, Player player, String planet) {
        if (!hasUnscoredSpendObjective(game, player)) return 0;
        Wallet now = Wallet.of(game, player);
        Wallet after = now.without(new Wallet.Payment(List.of(planet), List.of(), 0, 0));
        return Math.max(0, scorablePoints(game, player, now) - scorablePoints(game, player, after));
    }

    private static boolean hasUnscoredSpendObjective(Game game, Player player) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .anyMatch(objective -> !ObjectivePolicy.hasScored(game, player, objective)
                        && ObjectiveCatalog.spendCost(objective).isPresent());
    }

    private static int scorablePoints(Game game, Player player, Wallet wallet) {
        if (!Helper.canPlayerScorePOs(game, player)) return 0;
        int chances = ScoringReserve.scoringChances(game, player);
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(objective -> ObjectivePolicy.qualifies(game, player, wallet, objective))
                .map(ObjectivePolicy::victoryPoints)
                .sorted(Comparator.reverseOrder())
                .limit(chances)
                .mapToInt(Integer::intValue)
                .sum();
    }

    public static int pointDelta(Game game, Player player, int tradeGoodDelta) {
        return SearchMemo.remember(
                () -> computePointDelta(game, player, tradeGoodDelta), POINT_DELTA_MEMO, player, tradeGoodDelta);
    }

    private static int computePointDelta(Game game, Player player, int tradeGoodDelta) {
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
