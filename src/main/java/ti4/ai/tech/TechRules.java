package ti4.ai.tech;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class TechRules {

    private static final String REVIVE_PREFIX = "statusInfRevival_";
    private static final String SPINNER_START = "startYinSpinner";
    private static final String SPINNER_PLACE_PREFIX = "placeOneNDone_skipbuild_2gf_";
    private static final String SPINNER_KEY = "yinSpinner|";
    private static final String YIN_SPINNER = "yso";
    private static final String BIO_STIMS = "bs";
    private static final String BIO_STIMS_KEY = "bioStims|";
    private static final String BIO_STIMS_EXHAUSTED = "exhausted";
    private static final String BIO_STIMS_DONE = "readied";
    private static final String BIO_STIMS_EXHAUST = "exhaustTech_bs";
    private static final String BIO_STIMS_READY = "biostimsReady_";
    private static final String BIO_STIMS_READY_PLANET = "biostimsReady_planet_";
    private static final String END_OF_TURN_ABILITIES = "endOfTurnAbilities";
    private static final String MAGEN_PLACE_PREFIX = "useMagenDefense_";
    private static final String PSYCHOARCHAEOLOGY = "pa";
    private static final String PSYCHO_KEY = "psychoarchaeology|";
    private static final String PSYCHO_OPEN = "getPsychoButtons";
    private static final String PSYCHO_EXHAUST = "psychoExhaust_";
    private static final int TRADE_GOOD_VALUE = 1;

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

    public static Optional<AiDecision> placeMagenInfantry(AiTurnContext context) {
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        return Prompts.owned(visible, context.faction(), id -> id.startsWith(MAGEN_PLACE_PREFIX))
                .map(match -> match.press("place infantry with Magen Defense Grid"));
    }

    public static Optional<AiDecision> endOfTurn(AiTurnContext context, List<AiPrompt> thisTurn) {
        String key = BIO_STIMS_KEY + context.turnKey();
        String state = context.memory().get(key).orElse("");
        if (BIO_STIMS_EXHAUSTED.equals(state)) return readyWithBioStims(context, thisTurn, key);
        if (!state.isEmpty() || !context.seat().hasTechReady(BIO_STIMS)) return Optional.empty();
        if (bestTechSkipPlanet(context.game(), context.seat()).isEmpty()) return Optional.empty();
        Optional<Match> exhaust = Prompts.owned(thisTurn, context.faction(), BIO_STIMS_EXHAUST::equals);
        if (exhaust.isPresent()) {
            context.memory().put(key, BIO_STIMS_EXHAUSTED);
            return Optional.of(exhaust.get().press("exhaust Bio-Stims"));
        }
        return Prompts.owned(thisTurn, context.faction(), END_OF_TURN_ABILITIES::equals)
                .map(match -> match.press("use its end-of-turn abilities"));
    }

    private static Optional<AiDecision> readyWithBioStims(AiTurnContext context, List<AiPrompt> thisTurn, String key) {
        Optional<String> planet = bestTechSkipPlanet(context.game(), context.seat());
        Optional<Match> ready = planet.flatMap(
                        name -> Prompts.unowned(thisTurn, (BIO_STIMS_READY_PLANET + name)::equals))
                .or(() -> Prompts.unowned(thisTurn, id -> id.startsWith(BIO_STIMS_READY)));
        if (ready.isEmpty()) return Optional.empty();
        context.memory().put(key, BIO_STIMS_DONE);
        return Optional.of(ready.get().press("ready a planet with Bio-Stims"));
    }

    private static Optional<String> bestTechSkipPlanet(Game game, Player seat) {
        return seat.getExhaustedPlanets().stream()
                .filter(name -> ButtonHelper.checkForTechSkips(game, name))
                .max(Comparator.comparingDouble(
                        name -> BoardView.planetResources(game, name) + BoardView.planetInfluence(game, name)));
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.hasTech(PSYCHOARCHAEOLOGY)) return Optional.empty();
        boolean nothingToSave = ProductionPlanner.reserve(game, seat).isNone();
        Set<String> spare = seat.getReadiedPlanets().stream()
                .filter(name -> ButtonHelper.checkForTechSkips(game, name))
                .filter(name -> nothingToSave
                        || Math.max(BoardView.planetResources(game, name), BoardView.planetInfluence(game, name))
                                <= TRADE_GOOD_VALUE)
                .collect(Collectors.toSet());
        if (spare.isEmpty()) return Optional.empty();
        String key = PSYCHO_KEY + context.turnKey();
        if (context.memory().has(key)) {
            return Prompts.unowned(
                            thisTurn,
                            id -> id.startsWith(PSYCHO_EXHAUST)
                                    && spare.contains(StringUtils.removeStart(id, PSYCHO_EXHAUST)))
                    .map(match -> match.press("trade a planet for a trade good with Psychoarchaeology"));
        }
        Optional<Match> open = Prompts.owned(thisTurn, context.faction(), PSYCHO_OPEN::equals);
        open.ifPresent(match -> context.memory().put(key, "open"));
        return open.map(match -> match.press("use Psychoarchaeology before passing"));
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
