package ti4.ai.nekro;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class NekroRules {

    private static final Pattern PROPAGATION_GAIN =
            Pattern.compile("because of \\*\\*Propagation\\*\\*, you instead gain (\\d+) command tokens");
    private static final String COMMANDER = "Nekro Acidos";
    private static final String PROPAGATION_KEY = "propagationGain|";
    private static final long RECENT_MILLIS = 30 * 60_000L;

    public static Optional<AiDecision> propagationTokens(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasAbility("propagation")) return Optional.empty();
        Game game = context.game();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (context.now() - prompt.createdAtMillis() > RECENT_MILLIS) continue;
            Optional<Integer> target = propagationTarget(context, prompt);
            if (target.isEmpty()) continue;
            Optional<PromptButton> done = prompt.firstEnabled(button -> isOwnOrOpen(button, context.faction())
                    && "deleteButtons".equals(button.handlerId())
                    && button.label().startsWith("Done Gaining"));
            if (done.isEmpty()) continue;
            if (CommandTokenPolicy.netGainSoFar(game, seat) < target.get()) {
                Optional<PromptButton> grow = prompt.enabledHandler(CommandTokenPolicy.poolToGrow(game, seat));
                if (grow.isPresent()) {
                    return Optional.of(AiDecision.press(prompt, grow.get(), "gain a command token from Propagation"));
                }
            }
            context.memory().remove(PROPAGATION_KEY + prompt.messageId());
            return Optional.of(AiDecision.press(prompt, done.get(), "finish gaining command tokens from Propagation"));
        }
        return Optional.empty();
    }

    private static Optional<Integer> propagationTarget(AiTurnContext context, AiPrompt prompt) {
        String key = PROPAGATION_KEY + prompt.messageId();
        Optional<Integer> remembered =
                context.memory().get(key).filter(StringUtils::isNumeric).map(Integer::parseInt);
        if (remembered.isPresent()) return remembered;
        Matcher gain = PROPAGATION_GAIN.matcher(prompt.content());
        if (!gain.find() || !mentions(prompt, context.seat())) return Optional.empty();
        int target = Math.min(
                Integer.parseInt(gain.group(1)),
                CommandTokenPolicy.gainWithinReinforcements(context.game(), context.seat()));
        context.memory().put(key, String.valueOf(target));
        return Optional.of(target);
    }

    public static Optional<AiDecision> commanderDraw(AiTurnContext context) {
        Player seat = context.seat();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || !prompt.content().contains(COMMANDER) || !mentions(prompt, seat)) continue;
            Optional<PromptButton> draw = prompt.enabledHandler("draw_1_ACDelete");
            if (draw.isPresent() && !context.alreadyPressed(prompt, draw.get())) {
                return Optional.of(AiDecision.press(prompt, draw.get(), "draw an action card with Nekro Acidos"));
            }
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> dacxiveAnimators(AiTurnContext context) {
        return Prompts.owned(context.prompts(), context.faction(), id -> id.startsWith("dacxive_"))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("place an infantry with Dacxive Animators"));
    }

    static boolean mentions(AiPrompt prompt, Player seat) {
        String content = prompt.content();
        return content.contains(seat.getRepresentation())
                || content.contains(seat.getRepresentationUnfogged())
                || content.contains(seat.getRepresentationNoPing());
    }

    private static boolean isOwnOrOpen(PromptButton button, String faction) {
        return button.isUnowned() || button.isOwnedBy(faction);
    }
}
