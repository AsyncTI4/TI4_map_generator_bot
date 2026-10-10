package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;

@UtilityClass
class ComponentFlow {

    static final double STALL_ACTION_VALUE = 1.5;
    private static final String KEY = "relicAction|";
    private static final String FIELD = "~";
    private static final String COMPONENT_ACTION = "componentAction";

    static boolean taken(AiTurnContext context) {
        return context.memory().has(KEY + context.turnKey());
    }

    static void put(AiTurnContext context, String... fields) {
        context.memory().put(KEY + context.turnKey(), String.join(FIELD, fields));
    }

    static Optional<List<String>> fields(AiTurnContext context, String flow) {
        return context.memory()
                .get(KEY + context.turnKey())
                .map(value -> List.of(value.split(FIELD)))
                .filter(fields -> fields.getFirst().equals(flow));
    }

    static Optional<Match> menu(AiTurnContext context, List<AiPrompt> thisTurn) {
        return Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals);
    }

    static boolean tacticalActionBeats(AiTurnContext context, double actionValue) {
        Optional<TacticalPlan> plan = TacticalRules.rememberedPlan(context).or(() -> {
            Optional<TacticalPlan> computed = TacticalPlanner.best(context.game(), context.seat());
            computed.ifPresent(found -> TacticalRules.remember(context, found));
            return computed;
        });
        return plan.isPresent() && plan.get().score() >= actionValue;
    }
}
