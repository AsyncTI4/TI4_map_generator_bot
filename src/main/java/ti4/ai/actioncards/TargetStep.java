package ti4.ai.actioncards;

import java.util.Optional;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;

@FunctionalInterface
interface TargetStep {

    Optional<AiDecision> next(AiTurnContext context, String key, CardPlay play);
}
