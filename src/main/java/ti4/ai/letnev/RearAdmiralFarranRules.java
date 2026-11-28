package ti4.ai.letnev;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;

@UtilityClass
public class RearAdmiralFarranRules {

    public static final String GAIN = "gain1tgFromLetnevCommander";

    public static Optional<AiDecision> gainTradeGood(AiTurnContext context) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || !Prompts.mentions(prompt, context.seat())) continue;
            Optional<AiDecision> gain = prompt.firstEnabled(button -> button.isUnowned()
                            && GAIN.equals(button.handlerId())
                            && !context.alreadyPressed(prompt, button))
                    .map(button -> AiDecision.press(prompt, button, "gain a trade good from Rear Admiral Farran"));
            if (gain.isPresent()) return gain;
        }
        return Optional.empty();
    }
}
