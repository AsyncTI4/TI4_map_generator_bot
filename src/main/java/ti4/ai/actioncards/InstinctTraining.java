package ti4.ai.actioncards;

import java.util.Optional;
import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.StrategyTokenCost;
import ti4.game.Player;

@UtilityClass
class InstinctTraining {

    static final String PREFIX = "sabotage_xxcha_";
    private static final String TECH = "it";
    private static final String SPENT_KEY = "instinctTraining|";

    record Use(AiPrompt prompt, PromptButton button, double cost) {}

    static Optional<Use> available(AiTurnContext context, String title, Player cardPlayer) {
        Player seat = context.seat();
        if (!seat.hasTechReady(TECH)) return Optional.empty();
        OptionalDouble cost = StrategyTokenCost.toSpend(context.game(), seat);
        if (cost.isEmpty()) return Optional.empty();
        String handler = PREFIX + title + "_" + cardPlayer.getFaction();
        return Prompts.first(
                        Prompts.newestFirst(context.prompts()),
                        button -> button.isUnowned() && handler.equals(button.handlerId()))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> new Use(match.prompt(), match.button(), cost.getAsDouble()));
    }

    static boolean spentOn(AiTurnContext context, AiPrompt window) {
        return context.memory().has(SPENT_KEY + window.messageId());
    }

    static void remember(AiTurnContext context, AiPrompt window) {
        context.memory().put(SPENT_KEY + window.messageId(), "spent");
    }
}
