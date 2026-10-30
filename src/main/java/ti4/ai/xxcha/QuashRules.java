package ti4.ai.xxcha;

import java.util.Optional;
import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.agenda.AgendaQueue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.CardFacade;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class QuashRules {

    private static final String ABILITY = "quash";
    private static final String QUEUE_QUASH = "queueWhen_ability_quash";
    private static final String OPEN_WHENS = "queueAWhen";

    public static Optional<AiDecision> queue(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!canQuash(seat) || AgendaQueue.holdsQueuedWhen(game, seat) || !AgendaQueue.whenWindowOpen(game)) {
            return Optional.empty();
        }
        if (!worthQuashing(game, seat)) return Optional.empty();
        return pressHidden(context, QUEUE_QUASH, "queue Quash to discard a bad agenda")
                .or(() -> pressHidden(context, OPEN_WHENS, "open its when options to Quash"));
    }

    public static boolean canQuash(Player seat) {
        return seat.hasAbility(ABILITY) && seat.getStrategicCC() > 0;
    }

    public static boolean worthQuashing(Game game, Player seat) {
        OptionalDouble loss = AgendaPolicy.expectedLoss(game, seat);
        if (loss.isEmpty()) return AgendaPolicy.worthDiscarding(game, seat);
        return loss.getAsDouble() > CardFacade.strategyToken();
    }

    private static Optional<AiDecision> pressHidden(AiTurnContext context, String handlerId, String reason) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.enabledHandler(handlerId);
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(AiDecision.press(prompt, button.get(), reason));
            }
        }
        return Optional.empty();
    }
}
