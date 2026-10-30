package ti4.ai.secrets;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.helpers.Constants;

@UtilityClass
public class SecretScoring {

    private static final String REQUEST_KEY = "secretScoreRequest|";
    private static final String SHOW_SCORE_BUTTONS = "get_so_score_buttons";
    private static final String CARDS_INFO = "cardsInfo";
    private static final long REPLY_WAIT_MILLIS = 60_000L;

    public static Optional<AiDecision> score(AiTurnContext context, String secretId, String reason) {
        Player seat = context.seat();
        Integer handIndex = seat.getSecretsUnscored().get(secretId);
        if (handIndex == null) return Optional.empty();
        String handler = Constants.SO_SCORE_FROM_HAND + handIndex;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> score = prompt.enabledHandler(handler);
            if (score.isPresent()) {
                context.memory().remove(requestKey(context, secretId));
                return Optional.of(AiDecision.press(prompt, score.get(), reason));
            }
        }
        Optional<Long> requested = requestedAt(context, secretId);
        if (requested.isPresent() && context.now() < requested.get() + REPLY_WAIT_MILLIS) {
            return Optional.of(new AiDecision.Wait(requested.get() + REPLY_WAIT_MILLIS, "its secret objectives"));
        }
        Optional<AiDecision> ask = askFor(context, SHOW_SCORE_BUTTONS, "look at its secret objectives to score")
                .or(() -> askFor(context, CARDS_INFO, "refresh its cards info to score a secret"));
        ask.ifPresent(ignored -> context.memory().put(requestKey(context, secretId), String.valueOf(context.now())));
        return ask;
    }

    private static Optional<AiDecision> askFor(AiTurnContext context, String handlerId, String reason) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.enabledHandler(handlerId);
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(AiDecision.press(prompt, button.get(), reason));
            }
        }
        return Optional.empty();
    }

    private static Optional<Long> requestedAt(AiTurnContext context, String secretId) {
        return context.memory()
                .get(requestKey(context, secretId))
                .filter(StringUtils::isNumeric)
                .map(Long::parseLong);
    }

    private static String requestKey(AiTurnContext context, String secretId) {
        return REQUEST_KEY + secretId + "|" + context.game().getRound() + "|"
                + context.game().getPhaseOfGame();
    }
}
