package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TokenGainRules {

    private static final Pattern GAIN = Pattern.compile("gain (\\d+) command tokens?");
    private static final String TARGET_KEY = "exploreTokens|";
    private static final String DONE = "deleteButtons";
    private static final String DONE_LABEL = "Done Gaining";

    static Optional<AiDecision> next(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> done = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && DONE.equals(button.handlerId())
                    && button.label().startsWith(DONE_LABEL));
            Optional<Integer> target = done.isPresent() ? target(context, prompt) : Optional.empty();
            if (target.isEmpty()) continue;
            if (CommandTokenPolicy.netGainSoFar(game, seat) < target.get()) {
                Optional<PromptButton> grow = prompt.enabledHandler(CommandTokenPolicy.poolToGrow(game, seat));
                if (grow.isPresent()) {
                    return Optional.of(AiDecision.press(prompt, grow.get(), "gain a command token from exploring"));
                }
            }
            context.memory().remove(TARGET_KEY + prompt.messageId());
            return Optional.of(AiDecision.press(prompt, done.get(), "finish gaining command tokens"));
        }
        return Optional.empty();
    }

    private static Optional<Integer> target(AiTurnContext context, AiPrompt prompt) {
        String key = TARGET_KEY + prompt.messageId();
        Optional<Integer> remembered =
                context.memory().get(key).filter(StringUtils::isNumeric).map(Integer::parseInt);
        if (remembered.isPresent()) return remembered;
        Matcher gain = GAIN.matcher(prompt.content());
        if (!gain.find()) return Optional.empty();
        int target = Math.min(
                Integer.parseInt(gain.group(1)),
                CommandTokenPolicy.gainWithinReinforcements(context.game(), context.seat()));
        context.memory().put(key, String.valueOf(target));
        return Optional.of(target);
    }
}
