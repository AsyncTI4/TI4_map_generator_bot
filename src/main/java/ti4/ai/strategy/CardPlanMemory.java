package ti4.ai.strategy;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.StrategyCard;

@UtilityClass
public class CardPlanMemory {

    private static final String PLAN_PREFIX = "scPlan|";
    private static final String REPLANNED_PREFIX = "scPlanWas|";

    public static void write(AiTurnContext context, CardPlan plan) {
        rewrite(context, key(context, PLAN_PREFIX, plan.initiative()), plan.encode());
    }

    public static Optional<CardPlan> read(AiTurnContext context, int initiative) {
        return context.memory()
                .get(key(context, PLAN_PREFIX, initiative))
                .flatMap(encoded -> CardPlan.decode(StrategyCard.of(context.game(), initiative), initiative, encoded));
    }

    static void markReplanned(AiTurnContext context, int initiative, CardUse from) {
        rewrite(context, key(context, REPLANNED_PREFIX, initiative), from.name());
    }

    static Optional<CardUse> replannedFrom(AiTurnContext context, int initiative) {
        return context.memory().get(key(context, REPLANNED_PREFIX, initiative)).flatMap(CardPlanMemory::use);
    }

    private static Optional<CardUse> use(String name) {
        try {
            return Optional.of(CardUse.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void rewrite(AiTurnContext context, String key, String value) {
        context.memory().remove(key);
        context.memory().put(key, value);
    }

    private static String key(AiTurnContext context, String prefix, int initiative) {
        return prefix + context.game().getRound() + "|" + initiative;
    }
}
