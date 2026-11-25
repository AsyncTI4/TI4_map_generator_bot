package ti4.ai.xxcha;

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
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.strategy.SpendOutlook;
import ti4.ai.strategy.StructurePolicy;
import ti4.ai.tactical.GroundDrops;
import ti4.ai.tactical.GroundDrops.Drop;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class XxekirGromRules {

    public static final String HERO = "xxchahero-te";
    public static final String UNIT_PREFIX = "xxchaHeroTEStep2_";
    static final String PLACE_PREFIX = "placeOneNDone_skipbuildxxcha_";
    static final String MECH = "mech";
    static final String PDS = "pds";
    static final int PLACEMENTS = 4;
    static final double MIN_READYING_FOR_EARLY_PLAY = 4.0;
    private static final String HERO_BUTTON = "componentActionRes_leader_" + HERO;
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String KEY = "xxekirGrom|";
    private static final String MENU = "menu";
    private static final String PLACING = "placing";
    private static final String DONE = "done";
    private static final String FIELD = "~";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context)) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        if (unlocksSpendObjective(game, seat)) {
            return open(context, thisTurn, "to ready the planets it spends on a public objective");
        }
        if (!SpendOutlook.spendingAhead(game, seat)) return Optional.empty();
        double readying = readyingValue(game, seat);
        if (readying < MIN_READYING_FOR_EARLY_PLAY) return Optional.empty();
        return open(context, thisTurn, "to ready planets worth " + Math.round(readying) + " for its next actions");
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context) || !unlocksSpendObjective(context.game(), context.seat())) return Optional.empty();
        return open(context, thisTurn, "before passing, to ready the planets it spends on a public objective");
    }

    static boolean unlocksSpendObjective(Game game, Player seat) {
        List<String> readied = seat.getExhaustedPlanets().stream()
                .sorted(Comparator.comparingInt((String planet) -> -higherValue(game, planet))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(PLACEMENTS)
                .toList();
        return !readied.isEmpty() && SpendUnlock.unlocks(game, seat, readied, 0);
    }

    private static int higherValue(Game game, String planet) {
        return Math.max(BoardView.planetResources(game, planet), BoardView.planetInfluence(game, planet));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<String[]> state = state(context);
        if (state.isEmpty() || DONE.equals(state.get()[0])) return Optional.empty();
        long since = Long.parseLong(state.get()[1]);
        long last = Long.parseLong(state.get()[2]);
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since - CLOCK_SKEW_MILLIS)
                .toList();
        if (MENU.equals(state.get()[0])) return playHero(context, fresh, since);
        Optional<AiDecision> place = placeOne(context, fresh, since);
        if (place.isPresent()) return place;
        Optional<AiDecision> unit = chooseUnit(context, fresh, since);
        if (unit.isPresent()) return unit;
        if (context.now() >= last + STEP_WAIT_MILLIS) {
            remember(context, DONE, since);
            return Optional.empty();
        }
        return Optional.of(new AiDecision.Wait(last + STEP_WAIT_MILLIS, "Xxekir Grom"));
    }

    private static Optional<AiDecision> playHero(AiTurnContext context, List<AiPrompt> fresh, long since) {
        Optional<Match> hero = Prompts.owned(fresh, context.faction(), HERO_BUTTON::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (hero.isPresent()) {
            remember(context, PLACING, context.now());
            return Optional.of(hero.get().press("play Xxekir Grom"));
        }
        if (context.now() >= since + STEP_WAIT_MILLIS) {
            remember(context, DONE, context.now());
            return Optional.empty();
        }
        return Optional.of(new AiDecision.Wait(since + STEP_WAIT_MILLIS, "Xxekir Grom"));
    }

    private static Optional<AiDecision> chooseUnit(AiTurnContext context, List<AiPrompt> fresh, long since) {
        String unit = unitToPlace(context.game(), context.seat());
        for (AiPrompt prompt : fresh) {
            Optional<PromptButton> choice = prompt.enabledButtons().stream()
                    .filter(button ->
                            button.handlerId().equals(UNIT_PREFIX + unit) && !context.alreadyPressed(prompt, button))
                    .findFirst();
            if (choice.isPresent()) {
                remember(context, PLACING, since);
                return Optional.of(AiDecision.press(prompt, choice.get(), "place a " + unit + " with Xxekir Grom"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> placeOne(AiTurnContext context, List<AiPrompt> fresh, long since) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : fresh) {
            List<PromptButton> planets = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(PLACE_PREFIX)
                            && !context.alreadyPressed(prompt, button))
                    .toList();
            if (planets.isEmpty()) continue;
            String unit = unitOf(planets.getFirst());
            List<String> names = planets.stream().map(XxekirGromRules::planetOf).toList();
            String planet = bestPlanet(game, seat, unit, names);
            PromptButton button = planets.stream()
                    .filter(candidate -> planetOf(candidate).equals(planet))
                    .findFirst()
                    .orElse(planets.getFirst());
            remember(context, PLACING, since);
            return Optional.of(AiDecision.press(
                    prompt, button, "place a " + unit + " on " + planetOf(button) + " and ready it with Xxekir Grom"));
        }
        return Optional.empty();
    }

    static String unitToPlace(Game game, Player seat) {
        boolean pdsRoom = StructurePolicy.pds(game, seat).isPresent();
        if (pdsRoom && StructurePolicy.nearsStructureObjective(game, seat)) return PDS;
        if (GroundDrops.mechAvailable(game, seat)) return MECH;
        return pdsRoom ? PDS : MECH;
    }

    static String bestPlanet(Game game, Player seat, String unit, List<String> candidates) {
        Map<String, Double> readying = ReadyingValue.eachAlone(game, seat, exhausted(seat, candidates)).stream()
                .collect(Collectors.toMap(ReadyingValue.Pick::planet, ReadyingValue.Pick::value, Math::max));
        if (PDS.equals(unit)) {
            List<String> exhausted = exhausted(seat, candidates);
            List<String> pool = exhausted.isEmpty() ? candidates : exhausted;
            return StructurePolicy.planetFor(game, seat, PDS, pool)
                    .orElseGet(() -> bestByReadying(game, readying, candidates));
        }
        Map<String, Double> drops = GroundDrops.drops(game, seat, Optional.empty()).stream()
                .collect(Collectors.toMap(Drop::planet, Drop::mech, Math::max));
        return candidates.stream()
                .max(Comparator.comparingDouble(
                                (String planet) -> drops.getOrDefault(planet, 0.0) + readying.getOrDefault(planet, 0.0))
                        .thenComparingInt(planet -> BoardView.planetResources(game, planet))
                        .thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
    }

    private static String bestByReadying(Game game, Map<String, Double> readying, List<String> candidates) {
        return candidates.stream()
                .max(Comparator.comparingDouble((String planet) -> readying.getOrDefault(planet, 0.0))
                        .thenComparingInt(planet -> BoardView.planetResources(game, planet))
                        .thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
    }

    static double readyingValue(Game game, Player seat) {
        return ReadyingValue.eachAlone(game, seat, seat.getExhaustedPlanets()).stream()
                .map(ReadyingValue.Pick::value)
                .sorted(Comparator.reverseOrder())
                .limit(PLACEMENTS)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static List<String> exhausted(Player seat, List<String> candidates) {
        return candidates.stream().filter(seat.getExhaustedPlanets()::contains).toList();
    }

    private static String unitOf(PromptButton button) {
        return StringUtils.substringBefore(StringUtils.removeStart(button.handlerId(), PLACE_PREFIX), "_");
    }

    private static String planetOf(PromptButton button) {
        return StringUtils.substringAfter(StringUtils.removeStart(button.handlerId(), PLACE_PREFIX), "_");
    }

    private static boolean canStart(AiTurnContext context) {
        Game game = context.game();
        return context.seat().hasLeaderUnlocked(HERO)
                && context.isActivePlayer()
                && "action".equalsIgnoreCase(game.getPhaseOfGame())
                && !TacticalRules.inProgress(game, context.seat())
                && !TacticalRules.actionTaken(context)
                && state(context).isEmpty();
    }

    private static Optional<AiDecision> open(AiTurnContext context, List<AiPrompt> thisTurn, String why) {
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        remember(context, MENU, context.now());
        return Optional.of(menu.get().press("play Xxekir Grom " + why));
    }

    private static Optional<String[]> state(AiTurnContext context) {
        return context.memory()
                .get(KEY + context.turnKey())
                .map(value -> StringUtils.splitPreserveAllTokens(value, FIELD))
                .filter(fields ->
                        fields.length == 3 && StringUtils.isNumeric(fields[1]) && StringUtils.isNumeric(fields[2]));
    }

    private static void remember(AiTurnContext context, String stage, long since) {
        context.memory()
                .put(
                        KEY + context.turnKey(),
                        String.join(FIELD, stage, String.valueOf(since), String.valueOf(context.now())));
    }
}
