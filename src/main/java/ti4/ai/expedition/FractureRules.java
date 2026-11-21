package ti4.ai.expedition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Tile;
import ti4.helpers.Constants;

@UtilityClass
class FractureRules {

    static final String ROLL_PREFIX = "rollFracture";
    static final String INGRESS_PREFIX = "addIngressToken_";
    private static final String DONE = "deleteButtons";
    private static final String DONE_LABEL = "Done Resolving";
    private static final String KEY_SEPARATOR = "|";

    static Optional<AiDecision> roll(AiTurnContext context) {
        return Prompts.owned(
                        Prompts.newestFirst(context.prompts()), context.faction(), id -> id.startsWith(ROLL_PREFIX))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("roll for The Fracture"));
    }

    static Optional<AiDecision> placeIngress(AiTurnContext context) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            List<PromptButton> options = ingressOptions(context, prompt);
            if (options.isEmpty()) continue;
            if (placed(context, prompt) < allowed(options.getFirst())) {
                Optional<PromptButton> best = bestSite(context, options);
                if (best.isPresent()) {
                    return Optional.of(AiDecision.press(prompt, best.get(), "place an ingress token near itself"));
                }
            }
            Optional<PromptButton> done = prompt.firstEnabled(button ->
                    button.isUnowned() && DONE.equals(button.handlerId()) && DONE_LABEL.equals(button.label()));
            if (done.isPresent() && !context.alreadyPressed(prompt, done.get())) {
                return Optional.of(AiDecision.press(prompt, done.get(), "finish placing ingress tokens"));
            }
        }
        return Optional.empty();
    }

    private static Optional<PromptButton> bestSite(AiTurnContext context, List<PromptButton> options) {
        List<PromptButton> open = options.stream()
                .filter(button -> tileOf(context, button).isPresent())
                .toList();
        if (open.isEmpty()) return Optional.empty();
        SiteScore scores = SiteScore.of(context.game(), context.seat());
        return open.stream()
                .max(Comparator.comparingDouble(
                        button -> scores.ingress(tileOf(context, button).orElseThrow())));
    }

    private static List<PromptButton> ingressOptions(AiTurnContext context, AiPrompt prompt) {
        return prompt.enabledButtons().stream()
                .filter(button -> button.isOwnedBy(context.faction()))
                .filter(button -> button.handlerId().startsWith(INGRESS_PREFIX))
                .filter(button -> !context.alreadyPressed(prompt, button))
                .toList();
    }

    private static Optional<Tile> tileOf(AiTurnContext context, PromptButton button) {
        String position = StringUtils.substringBefore(StringUtils.removeStart(button.handlerId(), INGRESS_PREFIX), "_");
        return Optional.ofNullable(context.game().getTileByPosition(position))
                .filter(tile -> !tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS));
    }

    private static int allowed(PromptButton button) {
        String count = StringUtils.substringAfterLast(button.handlerId(), "_");
        return StringUtils.isNumeric(count) ? Integer.parseInt(count) : 1;
    }

    private static long placed(AiTurnContext context, AiPrompt prompt) {
        String start = prompt.messageId() + KEY_SEPARATOR;
        return context.pressedKeys().stream()
                .filter(key -> key.startsWith(start) && key.contains(INGRESS_PREFIX))
                .count();
    }
}
