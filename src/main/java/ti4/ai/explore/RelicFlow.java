package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;

interface RelicFlow {

    String name();

    String relicId();

    double netValue(AiTurnContext context);

    default String firstStage() {
        return RelicFlows.DONE;
    }

    default Optional<AiDecision> step(AiTurnContext context, List<AiPrompt> visible, List<String> fields) {
        return Optional.empty();
    }
}
