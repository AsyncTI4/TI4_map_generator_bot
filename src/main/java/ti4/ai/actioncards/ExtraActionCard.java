package ti4.ai.actioncards;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.tactical.TacticalRules;

@UtilityClass
class ExtraActionCard {

    static final String MASTER_PLAN = "master_plan";

    static AiCard masterPlan() {
        return AiActionCards.afterAction(MASTER_PLAN, alias -> alias.startsWith(MASTER_PLAN), ExtraActionCard::allow);
    }

    private static Optional<AiDecision> allow(AiTurnContext context, String key, CardPlay play) {
        TacticalRules.allowAnotherAction(context);
        PlayMemory.moveTo(context, key, play, Stage.DONE);
        return Optional.empty();
    }
}
