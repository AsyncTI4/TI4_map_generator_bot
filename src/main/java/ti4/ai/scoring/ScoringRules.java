package ti4.ai.scoring;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;

@UtilityClass
public class ScoringRules {

    private static final Set<String> PLANET_PROMPT_OBJECTIVES =
            Set.of("monument", "sway_council", "golden_age", "manipulate_law", "amass_wealth", "vast_reserves");

    public static AiDecision scoreAndExpectPayment(
            AiTurnContext context, AiPrompt prompt, ObjectivePolicy.ScoringChoice choice, String reason) {
        if (choice.payment().isPresent() && PLANET_PROMPT_OBJECTIVES.contains(choice.objectiveId())) {
            PaymentRules.expectWhenScored(
                    context, "an objective", choice.payment().get(), PaymentRules.OBJECTIVE_DONE);
        }
        return AiDecision.press(prompt, choice.button(), reason);
    }
}
