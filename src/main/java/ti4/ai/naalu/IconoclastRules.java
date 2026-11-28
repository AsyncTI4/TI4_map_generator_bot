package ti4.ai.naalu;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.GroundDrops;
import ti4.ai.tactical.GroundDrops.Drop;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class IconoclastRules {

    static final String MECH = "naalu_mech_te";
    static final String PLACE_PREFIX = "placeOneNDone_skipbuild_mech_";
    static final String DEPLOY_TEXT = "DEPLOY 1 Iconoclast";
    private static final String DECLINE = "deleteButtons";

    public static Optional<AiDecision> deploy(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.hasUnit(MECH)) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.content() == null || !prompt.content().contains(DEPLOY_TEXT)) continue;
            List<PromptButton> planets = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(PLACE_PREFIX)
                            && !context.alreadyPressed(prompt, button))
                    .toList();
            if (planets.isEmpty()) continue;
            if (!GroundDrops.mechAvailable(game, seat)) {
                return prompt.enabledHandler(DECLINE)
                        .map(button -> AiDecision.press(prompt, button, "no Iconoclast left in reinforcements"));
            }
            Map<String, Double> drops = GroundDrops.drops(game, seat, Optional.empty()).stream()
                    .collect(Collectors.toMap(Drop::planet, Drop::mech, Math::max));
            PromptButton best = planets.stream()
                    .max(Comparator.comparingDouble((PromptButton button) -> drops.getOrDefault(planetOf(button), 0.0))
                            .thenComparingInt(button -> BoardView.planetResources(game, planetOf(button)))
                            .thenComparing(IconoclastRules::planetOf, Comparator.reverseOrder()))
                    .orElseThrow();
            return Optional.of(AiDecision.press(prompt, best, "deploy a free Iconoclast on " + planetOf(best)));
        }
        return Optional.empty();
    }

    private static String planetOf(PromptButton button) {
        return StringUtils.removeStart(button.handlerId(), PLACE_PREFIX);
    }
}
