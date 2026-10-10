package ti4.ai.tactical;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class IntegratedEconomyRules {

    private static final String INTEGRATED_ECONOMY = "ie";
    private static final String OFFER_PREFIX = "integratedBuild_";
    private static final String DECLINE = "deleteButtons";
    private static final String SOURCE_PREFIX = "integrated";
    private static final String PLAN_KEY = "integratedEconomy|";
    private static final String OFFER_KEY = "integratedOffer|";
    private static final String FIELD = "#";

    public static Optional<AiDecision> next(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasTech(INTEGRATED_ECONOMY)) return Optional.empty();
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        Optional<AiDecision> build = continueBuild(context, visible);
        if (build.isPresent()) return build;
        return answerOffer(context, visible);
    }

    private static Optional<AiDecision> answerOffer(AiTurnContext context, List<AiPrompt> visible) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : visible) {
            Optional<PromptButton> offer = prompt.firstEnabled(
                    button -> button.isUnowned() && button.handlerId().startsWith(OFFER_PREFIX));
            if (offer.isEmpty() || !mentions(prompt, seat)) continue;
            String planet = StringUtils.removeStart(offer.get().handlerId(), OFFER_PREFIX);
            String key = OFFER_KEY + prompt.messageId();
            Tile tile = game.getTileFromPlanet(planet);
            if (tile == null
                    || !seat.getPlanets().contains(planet)
                    || context.memory().has(key)) continue;
            context.memory().put(key, "answered");
            BuildPlan plan = ProductionPlanner.planIntegrated(game, seat, tile, planet);
            if (plan.isEmpty()) {
                return prompt.firstEnabled(button -> button.isUnowned() && DECLINE.equals(button.handlerId()))
                        .map(button -> AiDecision.press(prompt, button, "skip the Integrated Economy build"));
            }
            context.memory().put(PLAN_KEY + TacticalRules.actionKey(context), planet + FIELD + plan.encode());
            return Optional.of(
                    AiDecision.press(prompt, offer.get(), "build on " + planet + " with Integrated Economy"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> continueBuild(AiTurnContext context, List<AiPrompt> visible) {
        String remembered = context.memory()
                .get(PLAN_KEY + TacticalRules.actionKey(context))
                .orElse("");
        String planet = StringUtils.substringBefore(remembered, FIELD);
        Optional<BuildPlan> plan = BuildPlan.decode(StringUtils.substringAfter(remembered, FIELD));
        Tile tile = planet.isBlank() ? null : context.game().getTileFromPlanet(planet);
        if (tile == null || plan.isEmpty()) return Optional.empty();
        String source = SOURCE_PREFIX + planet;
        return TacticalRules.placeUnits(context, visible, source, tile.getPosition(), plan)
                .or(() -> TacticalRules.payForUnits(context, visible, source));
    }

    private static boolean mentions(AiPrompt prompt, Player seat) {
        String content = prompt.content();
        return content.contains(seat.getRepresentation())
                || content.contains(seat.getRepresentationUnfogged())
                || content.contains(seat.getRepresentationNoPing());
    }
}
