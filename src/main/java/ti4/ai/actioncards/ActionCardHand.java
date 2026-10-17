package ti4.ai.actioncards;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.helpers.Constants;

@UtilityClass
class ActionCardHand {

    private static final String CARDS_INFO = "cardsInfo";
    private static final String REFRESH_KEY = "acHandRefresh|";
    private static final long REFRESH_WAIT_MILLIS = 20_000L;
    private static final long REFRESH_POLL_MILLIS = 3_000L;

    static Optional<Match> playButton(AiTurnContext context, int index, long since) {
        return playButton(context, index, "", since);
    }

    static Optional<Match> playButton(AiTurnContext context, int index, String suffix, long since) {
        String id = Constants.AC_PLAY_FROM_HAND + index + suffix;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> button =
                    prompt.firstEnabled(candidate -> candidate.isUnowned() && id.equals(candidate.handlerId()));
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(new Match(prompt, button.get()));
            }
        }
        return Optional.empty();
    }

    static Optional<AiDecision> requestHand(AiTurnContext context, String scope) {
        String key = REFRESH_KEY + scope;
        Optional<Long> asked =
                context.memory().get(key).filter(StringUtils::isNumeric).map(Long::parseLong);
        if (asked.isPresent()) {
            long until = asked.get() + REFRESH_WAIT_MILLIS;
            if (context.now() >= until) return Optional.empty();
            return Optional.of(
                    new AiDecision.Wait(Math.min(until, context.now() + REFRESH_POLL_MILLIS), "its action cards"));
        }
        Optional<Match> refresh = cardsInfoButton(context);
        refresh.ifPresent(ignored -> context.memory().put(key, String.valueOf(context.now())));
        return refresh.map(match -> match.press("bring its action cards back into view"));
    }

    private static Optional<Match> cardsInfoButton(AiTurnContext context) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.firstEnabled(candidate ->
                    CARDS_INFO.equals(candidate.handlerId()) && !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) return Optional.of(new Match(prompt, button.get()));
        }
        return Optional.empty();
    }
}
