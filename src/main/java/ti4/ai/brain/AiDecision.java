package ti4.ai.brain;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.ai.deal.HumanAiDeal;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;

public sealed interface AiDecision {

    record Press(AiPrompt prompt, PromptButton button, String reason) implements AiDecision {}

    record Unsure(AiPrompt prompt, String reason) implements AiDecision {}

    record Wait(long untilMillis, String reason) implements AiDecision {}

    record Idle() implements AiDecision {}

    record Announce(String text) implements AiDecision {}

    record Propose(HumanAiDeal deal) implements AiDecision {}

    record Post(String channelId, String text, List<Button> buttons) implements AiDecision {}

    static AiDecision press(AiPrompt prompt, PromptButton button, String reason) {
        return new Press(prompt, button, reason);
    }

    static AiDecision idle() {
        return new Idle();
    }
}
