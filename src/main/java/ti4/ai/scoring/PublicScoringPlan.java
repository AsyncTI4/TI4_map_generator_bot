package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.SearchMemo;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class PublicScoringPlan {

    private record Spend(int points, SpendCost cost) {}

    static int points(Game game, Player player, int slots) {
        if (slots <= 0) return 0;
        return SearchMemo.remember(() -> compute(game, player, slots), "public.scoring.plan", player, slots);
    }

    private static int compute(Game game, Player player, int slots) {
        List<String> candidates = ObjectivePolicy.scorablePublics(game, player);
        if (slots == 1)
            return candidates.stream()
                    .mapToInt(ObjectivePolicy::victoryPoints)
                    .max()
                    .orElse(0);
        List<Integer> free = candidates.stream()
                .filter(id -> !ObjectiveCatalog.isSpend(id))
                .map(ObjectivePolicy::victoryPoints)
                .sorted(Comparator.reverseOrder())
                .toList();
        List<Spend> spends = candidates.stream()
                .filter(ObjectiveCatalog::isSpend)
                .map(id -> new Spend(
                        ObjectivePolicy.victoryPoints(id),
                        ObjectiveCatalog.spendCost(id).orElseThrow()))
                .toList();
        return best(Wallet.of(game, player), free, spends, slots);
    }

    private static int best(Wallet wallet, List<Integer> free, List<Spend> spends, int slots) {
        if (slots == 0) return 0;
        int best = free.stream().limit(slots).mapToInt(Integer::intValue).sum();
        for (int index = 0; index < spends.size(); index++) {
            Spend spend = spends.get(index);
            var payment = wallet.plan(spend.cost());
            if (payment.isEmpty()) continue;
            List<Spend> remaining = new ArrayList<>(spends);
            remaining.remove(index);
            int points = spend.points() + best(wallet.without(payment.get()), free, remaining, slots - 1);
            best = Math.max(best, points);
        }
        return best;
    }
}
