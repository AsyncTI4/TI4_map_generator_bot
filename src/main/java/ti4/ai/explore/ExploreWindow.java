package ti4.ai.explore;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;

@UtilityClass
class ExploreWindow {

    static List<AiPrompt> prompts(AiTurnContext context) {
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .filter(prompt -> Prompts.createdThisTurn(context, prompt))
                .toList();
    }

    static boolean isOwn(AiTurnContext context, AiPrompt prompt) {
        if (Prompts.mentions(prompt, context.seat())) return true;
        return context.isActivePlayer() && !mentionsAnotherPlayer(context, prompt);
    }

    private static boolean mentionsAnotherPlayer(AiTurnContext context, AiPrompt prompt) {
        return context.game().getRealPlayers().stream()
                .filter(player -> player != context.seat())
                .anyMatch(player -> Prompts.mentions(prompt, player));
    }

    static boolean untouched(AiTurnContext context, AiPrompt prompt) {
        return prompt.buttons().stream().noneMatch(button -> context.alreadyPressed(prompt, button));
    }
}
