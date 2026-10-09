package ti4.ai.brain;

import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;

public sealed interface AiDecision {

    record Press(AiPrompt prompt, PromptButton button, String reason) implements AiDecision {}

    record Unsure(AiPrompt prompt, String reason) implements AiDecision {}

    record Wait(long untilMillis, String reason) implements AiDecision {}

    record Idle() implements AiDecision {}

    record Announce(String text) implements AiDecision {}

    static AiDecision press(AiPrompt prompt, PromptButton button, String reason) {
        return new Press(prompt, button, reason);
    }

    static AiDecision idle() {
        return new Idle();
    }
}
