package ti4.ai.sol;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.CombatRules;
import ti4.game.Game;
import ti4.game.Tile;

@UtilityClass
public class SolCommanderRules {

    public static final String PLACE_PREFIX = "utilizeSolCommander_";

    public static Optional<AiDecision> next(AiTurnContext context) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            Optional<PromptButton> place = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && button.handlerId().startsWith(PLACE_PREFIX)
                    && beforeFirstRound(context.game(), button)
                    && !context.alreadyPressed(prompt, button));
            if (place.isPresent()) {
                String planet = StringUtils.removeStart(place.get().handlerId(), PLACE_PREFIX);
                return Optional.of(AiDecision.press(
                        prompt, place.get(), "place 1 free infantry on " + planet + " with Claire Gibson"));
            }
        }
        return Optional.empty();
    }

    private static boolean beforeFirstRound(Game game, PromptButton button) {
        String planet = StringUtils.removeStart(button.handlerId(), PLACE_PREFIX);
        Tile tile = game.getTileFromPlanet(planet);
        return tile != null
                && game.getRealPlayers().stream()
                        .allMatch(player ->
                                CombatRules.tracker(game, player.getFaction(), tile.getPosition(), planet) == 0);
    }
}
