package ti4.ai.secrets;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SecretRedrawRules {

    private static final Set<String> REDRAW_ABILITIES = Set.of("planetAbilityExhaust_mrte", "planetAbilityExhaust_mc");
    private static final String DISCARD_PREFIX = "discardSecret_";
    private static final String REDRAW_SUFFIX = "redraw";
    private static final String REDRAW_KEY = "secretRedraw|";
    private static final String PENDING = "pending";

    public static Optional<AiDecision> endOfTurn(AiTurnContext context, List<AiPrompt> thisTurn) {
        Game game = context.game();
        Player seat = context.seat();
        if (context.memory().has(redrawKey(game)) || !SecretDraws.worthRedrawing(game, seat)) {
            return Optional.empty();
        }
        Optional<Prompts.Match> ability = Prompts.owned(thisTurn, context.faction(), REDRAW_ABILITIES::contains);
        if (ability.isEmpty()) return Optional.empty();
        context.memory().put(redrawKey(game), PENDING);
        return Optional.of(ability.get().press("exhaust Mecatol Rex to trade its weakest secret for a new one"));
    }

    public static Optional<AiDecision> discardForRedraw(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!context.memory().get(redrawKey(game)).filter(PENDING::equals).isPresent()) return Optional.empty();
        Optional<String> worst = SecretValue.lowestValueSecret(game, seat);
        if (worst.isEmpty()) return Optional.empty();
        String handler = DISCARD_PREFIX + seat.getSecretsUnscored().get(worst.get()) + REDRAW_SUFFIX;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.enabledHandler(handler);
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                context.memory().put(redrawKey(game), "done");
                return Optional.of(
                        AiDecision.press(prompt, button.get(), "discard its weakest secret to draw another"));
            }
        }
        return Optional.empty();
    }

    private static String redrawKey(Game game) {
        return REDRAW_KEY + game.getRound();
    }
}
