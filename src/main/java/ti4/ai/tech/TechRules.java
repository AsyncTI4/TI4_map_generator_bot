package ti4.ai.tech;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class TechRules {

    private static final String REVIVE_PREFIX = "statusInfRevival_";
    private static final String SPINNER_START = "startYinSpinner";
    private static final String SPINNER_PLACE_PREFIX = "placeOneNDone_skipbuild_2gf_";
    private static final String SPINNER_KEY = "yinSpinner|";
    private static final String YIN_SPINNER = "yso";

    public static Optional<AiDecision> reviveInfantry(AiTurnContext context) {
        Player seat = context.seat();
        if (seat.getStasisInfantry() <= 0) return Optional.empty();
        return bestOwned(context, REVIVE_PREFIX, button -> reviveScore(context.game(), seat, button))
                .map(match -> AiDecision.press(match.prompt(), match.button(), "revive infantry at home"));
    }

    public static Optional<AiDecision> startYinSpinner(AiTurnContext context, AiPrompt production) {
        if (!context.seat().hasTech(YIN_SPINNER)) return Optional.empty();
        String key = SPINNER_KEY + context.turnKey() + "|" + production.messageId();
        if (context.memory().has(key)) return Optional.empty();
        Optional<PromptButton> spinner =
                production.firstEnabled(button -> button.isUnowned() && SPINNER_START.equals(button.handlerId()));
        if (spinner.isEmpty()) return Optional.empty();
        context.memory().put(key, "started");
        context.memory().put(SPINNER_KEY + context.turnKey(), "placing");
        return Optional.of(AiDecision.press(production, spinner.get(), "place 2 infantry with Yin Spinner"));
    }

    public static Optional<AiDecision> placeSpinnerInfantry(AiTurnContext context) {
        String key = SPINNER_KEY + context.turnKey();
        if (!context.memory().get(key).filter("placing"::equals).isPresent()) return Optional.empty();
        Optional<Choice> best = bestOwned(
                context, SPINNER_PLACE_PREFIX, button -> spinnerScore(context.game(), context.seat(), button));
        best.ifPresent(choice -> context.memory().remove(key));
        return best.map(choice -> AiDecision.press(choice.prompt(), choice.button(), "place Yin Spinner infantry"));
    }

    private record Choice(AiPrompt prompt, PromptButton button, double score) {}

    private static Optional<Choice> bestOwned(
            AiTurnContext context, String prefix, ToDoubleFunction<PromptButton> score) {
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts());
        return prompts.stream()
                .filter(prompt -> !prompt.isHidden())
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isOwnedBy(context.faction())
                                && button.handlerId().startsWith(prefix))
                        .map(button -> new Choice(prompt, button, score.applyAsDouble(button))))
                .max(Comparator.comparingDouble(Choice::score));
    }

    private static double reviveScore(Game game, Player seat, PromptButton button) {
        String[] parts =
                StringUtils.removeStart(button.handlerId(), REVIVE_PREFIX).split("_");
        int amount = parts.length > 1 && StringUtils.isNumeric(parts[1]) ? Integer.parseInt(parts[1]) : 1;
        return amount * 10 + (hasDock(game, seat, parts[0]) ? 1 : 0);
    }

    private static double spinnerScore(Game game, Player seat, PromptButton button) {
        String target = StringUtils.removeStart(button.handlerId(), SPINNER_PLACE_PREFIX);
        if (hasDock(game, seat, target)) return 3;
        Planet planet = game.getPlanetsInfo().get(target);
        if (planet != null && seat.getPlanets().contains(target)) return 2 + planet.getResources() / 10.0;
        Tile tile = game.getTileByPosition(target.replace("space", ""));
        return tile != null && BoardView.count(BoardView.space(tile), seat, UnitType.Carrier) > 0 ? 1 : 0;
    }

    private static boolean hasDock(Game game, Player seat, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        return planet != null && BoardView.count(planet, seat, UnitType.Spacedock) > 0;
    }
}
