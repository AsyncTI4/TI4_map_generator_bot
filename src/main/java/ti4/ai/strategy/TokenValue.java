package ti4.ai.strategy;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
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

    static double lockedOut(PlanInputs in, String position, List<TacticalPlan> remembered) {
        List<TacticalPlan> plans = Stream.concat(remembered.stream(), in.ranked().stream())
                .sorted(Comparator.comparingDouble(TacticalPlan::score).reversed())
                .toList();
        List<TacticalPlan> open =
                plans.stream().filter(plan -> !needs(plan, position)).toList();
        return upcomingActions(in, plans) - upcomingActions(in, open);
    }

    private static boolean needs(TacticalPlan plan, String position) {
        return plan.target().equals(position)
                || plan.moves().stream().anyMatch(move -> move.origin().equals(position));
    }

    private static double upcomingActions(PlanInputs in, List<TacticalPlan> ranked) {
        int actions = Math.max(1, in.seat().getTacticalCC());
        return ranked.stream()
                .filter(distinctBy(TacticalPlan::target))
                .limit(actions)
                .mapToDouble(plan -> Math.max(0, plan.score()) * CardValueScale.TOKEN_ACTION_SHARE)
                .sum();
    }

    private static <T> Predicate<T> distinctBy(Function<T, String> key) {
        Set<String> seen = new HashSet<>();
        return item -> seen.add(key.apply(item));
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
