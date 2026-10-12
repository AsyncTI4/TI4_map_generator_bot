package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class CardFacade {

    public static double dockValue(Game game) {
        return game.getRound() <= CardValueScale.LAST_EARLY_DOCK_ROUND
                ? CardValueScale.DOCK_EARLY
                : CardValueScale.DOCK_LATE;
    }

    public static double tokenUnlockGain(Game game, Player seat, String position) {
        return PlanInputs.of(game, seat, PlanMoment.ACTION).unblockGain(position);
    }

    public static Optional<String> bestTokenToLift(Game game, Player seat, List<String> positions) {
        return PlannedPrimaries.tokenToRemove(game, seat, positions);
    }
}
