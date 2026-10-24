package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.tactical.TacticalPlan;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class CardFacade {

    public static double returnedTacticToken(Game game, Player seat) {
        PlanInputs in = PlanInputs.of(game, seat, PlanMoment.ACTION);
        List<TacticalPlan> ranked = in.ranked();
        int index = Math.max(0, seat.getTacticalCC());
        double plan = index < ranked.size() ? ranked.get(index).score() * CardValueScale.TOKEN_ACTION_SHARE : 0;
        return Math.max(CardValueScale.TOKEN_FLOOR, plan);
    }

    public static Optional<Double> stalledLeadershipValue(Game game, Player seat) {
        return LeadershipStall.value(game, seat);
    }

    public static double strategyToken() {
        return CardValueScale.STRATEGY_TOKEN;
    }

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
