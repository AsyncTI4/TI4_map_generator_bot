package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.message.GameMessage;
import ti4.service.strategycard.StrategyCardMessageService;

@UtilityClass
class CardMessages {

    private static final String STEP_KEY = "scStep|";

    static Optional<AiPrompt> playedThisTurn(AiTurnContext context, int initiative) {
        long turnStart = Prompts.turnStart(context);
        return cardMessage(context, initiative).filter(prompt -> prompt.createdAtMillis() >= turnStart);
    }

    static Optional<AiPrompt> cardMessage(AiTurnContext context, int initiative) {
        String noFollow = "sc_no_follow_" + initiative;
        Optional<String> recorded = StrategyCardMessageService.getStrategyCardMessage(
                        context.game().getName(), context.game().getRound(), initiative)
                .map(GameMessage::messageId);
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .filter(prompt -> recorded.map(prompt.messageId()::equals).orElse(true))
                .filter(prompt -> prompt.buttons().stream()
                        .anyMatch(button -> button.isUnowned() && noFollow.equals(button.handlerId())))
                .findFirst();
    }

    static Optional<AiDecision> pressOnce(AiTurnContext context, AiPrompt prompt, String handlerId, String reason) {
        Optional<PromptButton> button = prompt.enabledHandler(handlerId);
        if (button.isEmpty() || context.alreadyPressed(prompt, button.get())) return Optional.empty();
        return Optional.of(AiDecision.press(prompt, button.get(), reason));
    }

    static List<AiPrompt> hiddenSince(AiTurnContext context, long since) {
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(AiPrompt::isHidden)
                .filter(prompt -> prompt.createdAtMillis() >= since)
                .toList();
    }

    static boolean done(AiTurnContext context, String step) {
        return context.memory().has(stepKey(context, step));
    }

    static void markDone(AiTurnContext context, String step) {
        context.memory().put(stepKey(context, step), "done");
    }

    private static String stepKey(AiTurnContext context, String step) {
        return STEP_KEY + context.turnKey() + "|" + step;
    }
}
