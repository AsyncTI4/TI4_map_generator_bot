package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;

@UtilityClass
class CardPrompts {

    static final long CLOCK_SKEW_MILLIS = 5_000L;

    static Optional<Match> ownedPublic(AiTurnContext context, long since, Predicate<String> handler) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> button = prompt.firstEnabled(candidate -> candidate.isOwnedBy(context.faction())
                    && handler.test(candidate.handlerId())
                    && !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) return Optional.of(new Match(prompt, button.get()));
        }
        return Optional.empty();
    }

    static List<Match> ownedPublicAll(AiTurnContext context, long since, String prefix) {
        List<Match> matches = new ArrayList<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.isOwnedBy(context.faction())
                        && button.handlerId().startsWith(prefix)
                        && !context.alreadyPressed(prompt, button)) {
                    matches.add(new Match(prompt, button));
                }
            }
        }
        return matches;
    }
}
