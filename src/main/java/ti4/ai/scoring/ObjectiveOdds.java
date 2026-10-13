package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class ObjectiveOdds {

    private static final double BOARD_MET = 0.9;
    private static final double[] BOARD_SHORT_BY = {0.44, 0.25, 0.12};
    private static final double SPEND_MET = 0.72;
    private static final double[] SPEND_SHORT_BY = {0.6, 0.6, 0.45};
    private static final int FARTHEST_COUNTED = 3;

    public static double atLeastTwoByStatus(Game game, Player seat) {
        if (!Helper.canPlayerScorePOs(game, seat)) return 0;
        Wallet wallet = Wallet.of(game, seat);
        List<Double> chances = new ArrayList<>();
        double bestSpend = 0;
        for (String objective : game.getRevealedPublicObjectives().keySet()) {
            if (Mapper.getPublicObjective(objective) == null || ObjectivePolicy.hasScored(game, seat, objective)) {
                continue;
            }
            Optional<SpendCost> cost = ObjectiveCatalog.spendCost(objective);
            if (cost.isPresent()) {
                bestSpend = Math.max(bestSpend, spendChance(wallet, cost.get()));
            } else {
                chances.add(boardChance(game, seat, objective));
            }
        }
        if (bestSpend > 0) chances.add(bestSpend);
        return atLeastTwo(chances);
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
