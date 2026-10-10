package ti4.ai.tech;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Threats;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.SlingRelayRules;
import ti4.ai.tactical.TacticalRules;
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
    private static final String NULLIFICATION_PREFIX = "nullificationField_";
    private static final String DECLINE = "deleteButtons";
    private static final String NEURAL_PARASITE = "parasite-obs";
    private static final String PARASITE_KEY = "neuralParasite|";
    private static final String PARASITE_START = "startNeuralParasite";
    private static final String PARASITE_VICTIM = "victim";
    private static final String PARASITE_UNIT = "unit";
    private static final String PARASITE_DONE = "done";
    private static final String PARASITE_VICTIM_PREFIX = "neuralParasiteS2_";
    private static final String PARASITE_RESOLVE_PREFIX = "resolveNeuralParasite_";
    private static final double PLANET_TARGET_BONUS = 100;
    private static final double LAST_DEFENDER_BONUS = 10;
    private static final String SALVAGE_OPERATIONS = "so";
    private static final String SALVAGE_PREFIX = "salvageOps_";
    private static final String SALVAGE_KEY = "salvageOperations|";
    private static final String SALVAGE_DECLINE = "Decline";
    private static final String SALVAGE_BUILD_TEXT = "produce 1 ship that was destroyed in the combat";
    private static final String SELF_ASSEMBLY = "sar";
    private static final String ASSEMBLY_KEY = "selfAssembly|";
    private static final String ASSEMBLY_START = "sarMechStep1_";
    private static final String ASSEMBLY_PLACE = "sarMechStep2_";
    private static final double DOCK_PLANET_BONUS = 10;
    private static final String PRODUCTION_BIOMES = "pm";
    private static final String BIOMES_KEY = "productionBiomes|";
    private static final String BIOMES_MENU = "menu";
    private static final String BIOMES_RECIPIENT = "recipient";
    private static final String BIOMES_DONE = "done";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String BIOMES_EXHAUST = "exhaustTech_pm";
    private static final String BIOMES_TARGET = "productionBiomes_";

    public static Optional<AiDecision> reviveInfantry(AiTurnContext context) {
        Player seat = context.seat();
        if (seat.getStasisInfantry() <= 0) return Optional.empty();
        return bestOwned(context, REVIVE_PREFIX, button -> reviveScore(context.game(), seat, button))
                .map(match -> AiDecision.press(match.prompt(), match.button(), "revive infantry at home"));
    }

    public static Optional<AiDecision> startYinSpinner(AiTurnContext context, AiPrompt production) {
        if (!context.seat().hasTech(YIN_SPINNER)) return Optional.empty();
        String key = SPINNER_KEY + TacticalRules.actionKey(context) + "|" + production.messageId();
        if (context.memory().has(key)) return Optional.empty();
        Optional<PromptButton> spinner =
                production.firstEnabled(button -> button.isUnowned() && SPINNER_START.equals(button.handlerId()));
        if (spinner.isEmpty()) return Optional.empty();
        context.memory().put(key, "started");
        context.memory().put(SPINNER_KEY + TacticalRules.actionKey(context), "placing");
        return Optional.of(AiDecision.press(production, spinner.get(), "place 2 infantry with Yin Spinner"));
    }

    public static Optional<AiDecision> startSelfAssembly(AiTurnContext context, AiPrompt production) {
        Player seat = context.seat();
        if (!seat.hasTechReady(SELF_ASSEMBLY) || !mechInReinforcements(context.game(), seat)) return Optional.empty();
        String key = ASSEMBLY_KEY + TacticalRules.actionKey(context) + "|" + production.messageId();
        if (context.memory().has(key)) return Optional.empty();
        Optional<PromptButton> start = production.firstEnabled(
                button -> button.isUnowned() && button.handlerId().startsWith(ASSEMBLY_START));
        if (start.isEmpty()) return Optional.empty();
        context.memory().put(key, "started");
        context.memory().put(ASSEMBLY_KEY + TacticalRules.actionKey(context), "placing");
        return Optional.of(AiDecision.press(production, start.get(), "place a mech with Self-Assembly Routines"));
    }

    public static Optional<AiDecision> placeSelfAssemblyMech(AiTurnContext context) {
        String key = ASSEMBLY_KEY + TacticalRules.actionKey(context);
        if (context.memory().get(key).filter("placing"::equals).isEmpty()) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        Optional<Match> best = bestButton(
                Prompts.newestFirst(context.prompts()),
                button -> button.isUnowned() && button.handlerId().startsWith(ASSEMBLY_PLACE),
                button -> {
                    String planet = StringUtils.substringBefore(
                            StringUtils.removeStart(button.handlerId(), ASSEMBLY_PLACE), "_");
                    return (hasDock(game, seat, planet) ? DOCK_PLANET_BONUS : 0)
                            + BoardView.planetResources(game, planet);
                });
        best.ifPresent(match -> context.memory().remove(key));
        return best.map(match -> match.press("place the Self-Assembly Routines mech"));
    }

    private static boolean mechInReinforcements(Game game, Player seat) {
        int cap = seat.getUnitCap(UnitType.Mech.getValue());
        return cap <= 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, UnitType.Mech.getValue()) < cap;
    }

    public static Optional<AiDecision> placeSpinnerInfantry(AiTurnContext context) {
        String key = SPINNER_KEY + TacticalRules.actionKey(context);
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
        return startProductionBiomes(context, thisTurn)
                .or(() -> SlingRelayRules.start(context, thisTurn))
                .or(() -> psychoarchaeology(context, thisTurn));
    }

    private static Optional<AiDecision> startProductionBiomes(AiTurnContext context, List<AiPrompt> thisTurn) {
        Player seat = context.seat();
        String key = BIOMES_KEY + context.turnKey();
        if (!seat.hasTechReady(PRODUCTION_BIOMES) || context.memory().has(key)) return Optional.empty();
        int reserved = ScoringReserve.of(context.game(), seat).tokens();
        if (seat.getStrategicCC() < 1 || seat.getStrategicCC() + seat.getTacticalCC() - 1 < reserved) {
            return Optional.empty();
        }
        Optional<Match> component = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals);
        component.ifPresent(match -> context.memory().put(key, BIOMES_MENU));
        return component.map(match -> match.press("take 4 trade goods with Production Biomes instead of passing"));
    }

    public static Optional<AiDecision> continueProductionBiomes(AiTurnContext context) {
        String key = BIOMES_KEY + context.turnKey();
        String state = context.memory().get(key).orElse("");
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        if (BIOMES_MENU.equals(state)) {
            Optional<Match> exhaust = Prompts.owned(visible, context.faction(), BIOMES_EXHAUST::equals);
            exhaust.ifPresent(match -> context.memory().put(key, BIOMES_RECIPIENT));
            return exhaust.map(match -> match.press("exhaust Production Biomes"));
        }
        if (!BIOMES_RECIPIENT.equals(state)) return Optional.empty();
        Game game = context.game();
        Optional<Match> recipient = visible.stream()
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button ->
                                button.isUnowned() && button.handlerId().startsWith(BIOMES_TARGET))
                        .map(button -> new Match(prompt, button)))
                .min(Comparator.comparingInt(match -> victoryPoints(
                        game, StringUtils.removeStart(match.button().handlerId(), BIOMES_TARGET))));
        recipient.ifPresent(match -> context.memory().put(key, BIOMES_DONE));
        return recipient.map(
                match -> match.press("give Production Biomes' 2 trade goods to the player furthest behind"));
    }

    public static Optional<AiDecision> nullificationField(AiTurnContext context) {
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        Optional<Match> use = Prompts.owned(visible, context.faction(), id -> id.startsWith(NULLIFICATION_PREFIX));
        if (use.isEmpty()) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        String[] target = StringUtils.removeStart(use.get().button().handlerId(), NULLIFICATION_PREFIX)
                .split("_");
        Tile tile = game.getTileByPosition(target[0]);
        Player active = target.length > 1 ? game.getPlayerFromColorOrFaction(target[1]) : null;
        int reserved = ScoringReserve.of(game, seat).tokens();
        boolean spareToken = seat.getStrategicCC() >= 1 && seat.getStrategicCC() + seat.getTacticalCC() - 1 >= reserved;
        if (tile != null && active != null && spareToken && threatened(game, seat, active, tile)) {
            return Optional.of(use.get().press("end " + active.getFaction() + "'s turn with Nullification Field"));
        }
        return use.get()
                .prompt()
                .firstEnabled(button -> button.isOwnedBy(context.faction()) && DECLINE.equals(button.handlerId()))
                .map(button -> AiDecision.press(use.get().prompt(), button, "let the activation stand"));
    }

    private static boolean threatened(Game game, Player seat, Player active, Tile tile) {
        double incoming = Threats.incomingFleetCost(game, active, tile);
        if (incoming <= 0) return false;
        if (tile == seat.getHomeSystemTile()) return true;
        double defending = BoardView.ships(BoardView.space(tile), seat).entrySet().stream()
                .mapToDouble(entry -> BoardView.model(seat, entry.getKey())
                                .map(model -> (double) model.getCost())
                                .orElse(0.0)
                        * entry.getValue())
                .sum();
        return incoming >= defending;
    }

    public static Optional<AiDecision> neuralParasite(AiTurnContext context) {
        if (!context.seat().hasTech(NEURAL_PARASITE) || !context.isActivePlayer()) return Optional.empty();
        String key = PARASITE_KEY + context.turnKey();
        String state = context.memory().get(key).orElse("");
        List<AiPrompt> turn = Prompts.thisTurn(context).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        Game game = context.game();
        Optional<Match> next =
                switch (state) {
                    case "" -> Prompts.unowned(turn, PARASITE_START::equals);
                    case PARASITE_VICTIM ->
                        bestButton(
                                turn,
                                button -> button.isOwnedBy(context.faction())
                                        && button.handlerId().startsWith(PARASITE_VICTIM_PREFIX),
                                button -> leaderScore(
                                        game, StringUtils.removeStart(button.handlerId(), PARASITE_VICTIM_PREFIX)));
                    case PARASITE_UNIT ->
                        bestButton(
                                turn,
                                button ->
                                        button.isUnowned() && button.handlerId().startsWith(PARASITE_RESOLVE_PREFIX),
                                TechRules::parasiteTargetScore);
                    default -> Optional.empty();
                };
        if (next.isEmpty()) return Optional.empty();
        String following =
                switch (state) {
                    case "" -> PARASITE_VICTIM;
                    case PARASITE_VICTIM -> PARASITE_UNIT;
                    default -> PARASITE_DONE;
                };
        context.memory().put(key, following);
        return Optional.of(next.get().press("destroy an enemy infantry with Neural Parasite"));
    }

    private static Optional<Match> bestButton(
            List<AiPrompt> prompts, Predicate<PromptButton> wanted, ToDoubleFunction<PromptButton> score) {
        return prompts.stream()
                .flatMap(prompt ->
                        prompt.enabledButtons().stream().filter(wanted).map(button -> new Match(prompt, button)))
                .max(Comparator.comparingDouble(match -> score.applyAsDouble(match.button())));
    }

    private static double parasiteTargetScore(PromptButton button) {
        String[] parts = StringUtils.removeStart(button.handlerId(), PARASITE_RESOLVE_PREFIX)
                .split("_");
        boolean onPlanet = parts.length > 1 && !BoardView.SPACE.equals(parts[1]);
        String count = StringUtils.substringBetween(button.label(), "(", ")");
        int infantry = StringUtils.isNumeric(count) ? Integer.parseInt(count) : Integer.MAX_VALUE;
        return (onPlanet ? PLANET_TARGET_BONUS : 0) + (infantry == 1 ? LAST_DEFENDER_BONUS : 0) - infantry;
    }

    public static Optional<AiDecision> salvageOperations(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            Optional<PromptButton> decline = prompt.firstEnabled(button ->
                    button.isUnowned() && DECLINE.equals(button.handlerId()) && SALVAGE_DECLINE.equals(button.label()));
            if (decline.isPresent() && prompt.content().contains(SALVAGE_BUILD_TEXT) && mentions(prompt, seat)) {
                return Optional.of(AiDecision.press(prompt, decline.get(), "skip Salvage Operations' rebuild"));
            }
            Optional<PromptButton> salvage = prompt.firstEnabled(
                    button -> button.isUnowned() && button.handlerId().startsWith(SALVAGE_PREFIX));
            if (salvage.isEmpty() || !seat.hasTech(SALVAGE_OPERATIONS)) continue;
            String key = SALVAGE_KEY + prompt.messageId();
            Tile tile =
                    game.getTileByPosition(StringUtils.removeStart(salvage.get().handlerId(), SALVAGE_PREFIX));
            if (tile == null || context.memory().has(key) || !spaceCombatDecided(game, seat, tile)) continue;
            context.memory().put(key, "pressed");
            return Optional.of(AiDecision.press(prompt, salvage.get(), "gain a trade good with Salvage Operations"));
        }
        return Optional.empty();
    }

    private static boolean mentions(AiPrompt prompt, Player seat) {
        String content = prompt.content();
        return content.contains(seat.getRepresentation())
                || content.contains(seat.getRepresentationUnfogged())
                || content.contains(seat.getRepresentationNoPing());
    }

    private static boolean spaceCombatDecided(Game game, Player seat, Tile tile) {
        boolean own = BoardView.hasOwnShips(seat, tile);
        boolean enemy = BoardView.hasEnemyShips(game, seat, tile);
        return own != enemy;
    }

    private static int victoryPoints(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player == null ? Integer.MAX_VALUE : player.getTotalVictoryPoints();
    }

    private static int leaderScore(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player == null ? -1 : player.getTotalVictoryPoints();
    }

    private static Optional<AiDecision> psychoarchaeology(AiTurnContext context, List<AiPrompt> thisTurn) {
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
