package ti4.ai.yin;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tech.TechRules;

@UtilityClass
public class YinSpinnerRules {

    public static final String START = "startYinSpinner";
    static final String TECH = "yso";
    static final String BUILD_PREFIX = "placeOneNDone_dontskip";
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static Optional<AiDecision> afterOtherProduction(AiTurnContext context) {
        if (!context.seat().hasTech(TECH)) return Optional.empty();
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts());
        for (AiPrompt prompt : prompts) {
            if (!Prompts.mentions(prompt, context.seat()) || !onlySpinner(prompt)) continue;
            if (buildStillOpen(context, prompts, prompt)) continue;
            Optional<AiDecision> spin = TechRules.startYinSpinner(context, prompt);
            if (spin.isPresent()) return spin;
        }
        return Optional.empty();
    }

    private static boolean buildStillOpen(AiTurnContext context, List<AiPrompt> prompts, AiPrompt spinner) {
        return prompts.stream()
                .filter(prompt -> prompt.createdAtMillis() >= spinner.createdAtMillis() - CLOCK_SKEW_MILLIS)
                .anyMatch(prompt -> prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                                && button.handlerId().startsWith(BUILD_PREFIX))
                        .isPresent());
    }

    private static boolean onlySpinner(AiPrompt prompt) {
        return !prompt.enabledButtons().isEmpty()
                && prompt.enabledButtons().stream().allMatch(button -> START.equals(button.handlerId()));
    }
}
