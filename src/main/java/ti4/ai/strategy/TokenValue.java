package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.tactical.TacticalPlan;

@UtilityClass
class TokenValue {

    static double tacticTokens(PlanInputs in, int gained) {
        return tokens(in, gained, CardValueScale.TOKEN_FLOOR);
    }

    static double carriedTokens(PlanInputs in, int gained) {
        return tokens(in, gained, CardValueScale.HELD_TOKEN);
    }

    private static double tokens(PlanInputs in, int gained, double floor) {
        int usable = Math.min(gained, in.reinforcements());
        int start = Math.max(0, in.seat().getTacticalCC());
        List<TacticalPlan> ranked = in.ranked();
        double value = 0;
        for (int i = 0; i < usable; i++) {
            int index = start + i;
            double planValue =
                    index < ranked.size() ? ranked.get(index).score() * CardValueScale.TOKEN_ACTION_SHARE : 0;
            value += Math.max(floor, planValue);
        }
        return value;
    }

    static double plannedActions(PlanInputs in, int gained) {
        int usable = Math.min(gained, in.reinforcements());
        int start = Math.max(0, in.seat().getTacticalCC());
        List<TacticalPlan> ranked = in.ranked();
        double value = 0;
        for (int index = start; index < Math.min(ranked.size(), start + usable); index++) {
            value += Math.max(0, ranked.get(index).score()) * CardValueScale.TOKEN_ACTION_SHARE;
        }
        return value;
    }
}
