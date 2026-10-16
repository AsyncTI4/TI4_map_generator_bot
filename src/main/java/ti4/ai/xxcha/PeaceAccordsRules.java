package ti4.ai.xxcha;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.PeaceAccords;

@UtilityClass
public class PeaceAccordsRules {

    private static final String DECLINE = "deleteButtons";
    private static final long OFFER_LIFETIME_MILLIS = 30 * 60_000L;

    public static Optional<AiDecision> resolve(AiTurnContext context) {
        if (!PeaceAccords.applies(context.game(), context.seat())) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || context.now() - prompt.createdAtMillis() > OFFER_LIFETIME_MILLIS) continue;
            List<PromptButton> offered = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(PeaceAccords.BUTTON_PREFIX))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .toList();
            if (offered.isEmpty()) continue;
            Optional<AiDecision> answer = claim(context, prompt, offered).or(() -> decline(context, prompt));
            if (answer.isPresent()) return answer;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> claim(AiTurnContext context, AiPrompt prompt, List<PromptButton> offered) {
        return PeaceAccords.claims(context.game(), context.seat()).stream()
                .flatMap(claim -> offered.stream()
                        .filter(button ->
                                PeaceAccords.planetOf(button.handlerId()).equals(claim.planet()))
                        .map(button ->
                                AiDecision.press(prompt, button, "claim " + claim.planet() + " with Peace Accords")))
                .findFirst();
    }

    private static Optional<AiDecision> decline(AiTurnContext context, AiPrompt prompt) {
        return prompt.enabledHandler(DECLINE)
                .filter(button -> !context.alreadyPressed(prompt, button))
                .map(button -> AiDecision.press(prompt, button, "decline Peace Accords: no planet it can claim"));
    }
}
