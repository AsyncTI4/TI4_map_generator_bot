package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.model.Mapper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class ObjectiveOdds {

    private static final double BOARD_MET = 0.9;
    private static final double[] BOARD_SHORT_BY = {0.44, 0.25, 0.12};
    private static final double SPEND_MET = 0.72;
    private static final double[] SPEND_SHORT_BY = {0.6, 0.6, 0.45};
    private static final int FARTHEST_COUNTED = 3;

    private record Chance(double byStatus, boolean metNow) {}

    public static double extraScoreWithImperial(Game game, Player seat) {
        List<Chance> chances = chances(game, seat);
        Optional<Chance> lockedIn =
                chances.stream().filter(Chance::metNow).min(Comparator.comparingDouble(Chance::byStatus));
        if (lockedIn.isEmpty()) return atLeastTwo(byStatus(chances));
        List<Chance> rest = new ArrayList<>(chances);
        rest.remove(lockedIn.get());
        return atLeastOne(byStatus(rest));
    }

    public static double atLeastTwoByStatus(Game game, Player seat) {
        return atLeastTwo(byStatus(chances(game, seat)));
    }

    private static List<Chance> chances(Game game, Player seat) {
        if (!Helper.canPlayerScorePOs(game, seat)) return List.of();
        Wallet wallet = Wallet.of(game, seat);
        List<Chance> chances = new ArrayList<>();
        double bestSpend = 0;
        boolean spendMetNow = false;
        for (String objective : game.getRevealedPublicObjectives().keySet()) {
            if (Mapper.getPublicObjective(objective) == null || ObjectivePolicy.hasScored(game, seat, objective)) {
                continue;
            }
            boolean metNow = ObjectivePolicy.qualifies(game, seat, wallet, objective);
            Optional<SpendCost> cost = ObjectiveCatalog.spendCost(objective);
            if (cost.isPresent()) {
                bestSpend = Math.max(bestSpend, spendChance(wallet, cost.get()));
                spendMetNow |= metNow;
            } else {
                chances.add(new Chance(boardChance(game, seat, objective), metNow));
            }
        }
        if (bestSpend > 0) chances.add(new Chance(bestSpend, spendMetNow));
        return chances;
    }

    private static List<Double> byStatus(List<Chance> chances) {
        return chances.stream().map(Chance::byStatus).toList();
    }

    static double atLeastOne(List<Double> chances) {
        double none = 1;
        for (double chance : chances) none *= 1 - chance;
        return 1 - none;
    }

    static double spendChance(Wallet wallet, SpendCost cost) {
        int shortBy = wallet.shortfall(cost, FARTHEST_COUNTED);
        return shortBy == 0 ? SPEND_MET : SPEND_SHORT_BY[Math.min(FARTHEST_COUNTED, shortBy) - 1];
    }

    static double boardChance(Game game, Player seat, String objective) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        if (threshold <= 0) return 0;
        int shortBy =
                Math.max(0, threshold - ListPlayerInfoService.getPlayerProgressOnObjective(objective, game, seat));
        return shortBy == 0 ? BOARD_MET : BOARD_SHORT_BY[Math.min(FARTHEST_COUNTED, shortBy) - 1];
    }

    static double atLeastTwo(List<Double> chances) {
        double none = 1;
        double one = 0;
        for (double chance : chances) {
            one = one * (1 - chance) + none * chance;
            none *= 1 - chance;
        }
        return Math.max(0, 1 - none - one);
    }
}
