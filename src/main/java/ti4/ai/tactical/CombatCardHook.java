package ti4.ai.tactical;

import java.util.Optional;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;

@FunctionalInterface
public interface CombatCardHook {

    CombatCardHook NONE = (context, moment) -> Optional.empty();

    Optional<AiDecision> atRoundStart(AiTurnContext context, CombatMoment moment);
}
