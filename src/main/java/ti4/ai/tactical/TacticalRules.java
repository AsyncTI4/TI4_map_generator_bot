package ti4.ai.tactical;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.promissory.CeasefireRules;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class TacticalRules {

    static final String PLAN_KEY = "tacticalPlan|";
    static final String BUILD_KEY = "buildPlan|";
    static final String PICKER_PRESSES_KEY = "pickerPresses|";
    static final String EXHAUSTED_ORIGIN_KEY = "exhaustedOrigin|";
    static final String COMBAT_EXPECTED_KEY = "combatExpectedSince|";
    private static final int MAX_PICKER_PRESSES = 4;
    private static final String RING_TILE_PREFIX = "ringTile_";
    private static final int LARGE_RING = 5;
    private static final long COMBAT_WAIT_MILLIS = 60_000L;
    private static final long COMBAT_THREAD_GRACE_MILLIS = 90_000L;
    public static final String WARFARE_SOURCE = "warfare";
    private static final String TACTICAL_SOURCE = "tacticalAction";
    private static final String DONE_PREFIX = "deleteButtons_";
    private static final String DONE_PRODUCING = "Done Producing Units";
    private static final String DONE_EXHAUSTING = "Done Exhausting Planets";
    private static final String SARWEEN = "sarween";
    private static final int CUSTODIANS_COST = 6;
    private static final Set<String> STATE_SEGMENTS =
            Set.of(UnitState.dmg.name(), UnitState.glv.name(), UnitState.dmg_glv.name());

    public static boolean inProgress(Game game, Player seat) {
        String summary = game.getStoredValue("currentActionSummary" + seat.getFaction());
        return StringUtils.isNotBlank(game.getCurrentActiveSystem())
                && summary.contains(" Activated ")
                && !"yes".equals(game.getStoredValue("gameEventTacticalLogged"));
    }

    public static boolean actionTaken(AiTurnContext context) {
        String faction = context.faction();
        return Prompts.thisTurn(context).stream()
                .anyMatch(prompt -> prompt.buttons().stream()
                        .filter(button -> button.isOwnedBy(faction))
                        .filter(button -> "tacticalAction".equals(button.handlerId())
                                || button.handlerId().startsWith("strategicAction_"))
                        .anyMatch(button -> context.alreadyPressed(prompt, button)));
    }

    public static boolean pickingSystem(AiTurnContext context) {
        String summary = context.game().getStoredValue("currentActionSummary" + context.faction());
        return !summary.contains(" Activated ")
                && Prompts.thisTurn(context).stream().anyMatch(TacticalRules::isPicker);
    }

    public static Optional<AiDecision> start(AiTurnContext context) {
        List<AiPrompt> turn = Prompts.thisTurn(context);
        List<AiPrompt> pickers = turn.stream().filter(TacticalRules::isPicker).toList();
        Optional<AiPrompt> picker = pickers.stream().findFirst();
        Optional<TacticalPlan> plan = rememberedPlan(context).or(() -> {
            Optional<TacticalPlan> computed = TacticalPlanner.best(context.game(), context.seat());
            computed.ifPresent(found -> context.memory().put(PLAN_KEY + context.turnKey(), found.encode()));
            return computed;
        });
        if (plan.isEmpty()) {
            return picker.map(found -> new AiDecision.Unsure(found, "it found no system worth activating"));
        }
        if (picker.isPresent()) return Optional.of(pickSystem(context, pickers, plan.get()));
        return Prompts.owned(turn, context.faction(), "tacticalAction"::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("start a tactical action: " + describe(plan.get())));
    }

    public static void remember(AiTurnContext context, TacticalPlan plan) {
        context.memory().put(PLAN_KEY + context.turnKey(), plan.encode());
    }

    public static boolean activatedThisTurn(AiTurnContext context) {
        return context.game()
                .getStoredValue("currentActionSummary" + context.faction())
                .contains(" Activated ");
    }

    public static Optional<AiDecision> continueAction(AiTurnContext context) {
        Game game = context.game();
        String target = game.getCurrentActiveSystem();
        Tile tile = StringUtils.isBlank(target) ? null : game.getTileByPosition(target);
        if (tile == null) return Optional.empty();
        Player seat = context.seat();
        List<AiPrompt> turn = Prompts.thisTurn(context);
        Optional<TacticalPlan> plan = plan(context, target);

        Optional<AiDecision> movement = movement(context, turn, target, plan);
        if (movement.isPresent()) return movement;
        Optional<Match> explore = Prompts.owned(turn, context.faction(), id -> id.startsWith("movedNExplored_"));
        if (explore.isPresent()) return Optional.of(explore.get().press("explore a newly gained planet"));
        Optional<Match> relic = Prompts.owned(turn, context.faction(), "drawRelic"::equals);
        if (relic.isPresent()) return Optional.of(relic.get().press("draw a relic"));
        if (BoardView.hasOwnShips(seat, tile) && BoardView.hasEnemyShips(game, seat, tile)) {
            return waitForCombat(context, turn);
        }
        Optional<AiDecision> landing = landing(context, turn, tile, plan);
        if (landing.isPresent()) return landing;
        if (groundCombatOngoing(game, seat, tile)) return waitForCombat(context, turn);
        Optional<AiDecision> pay = payForUnits(context, turn, TACTICAL_SOURCE);
        if (pay.isPresent()) return pay;
        Optional<BuildPlan> buildPlan =
                context.memory().get(BUILD_KEY + context.turnKey()).flatMap(BuildPlan::decode);
        Optional<AiDecision> place = placeUnits(context, turn, TACTICAL_SOURCE, target, buildPlan);
        if (place.isPresent()) return place;
        return build(context, turn, tile);
    }

    public static Optional<TacticalPlan> rememberedPlan(AiTurnContext context) {
        return context.memory().get(PLAN_KEY + context.turnKey()).flatMap(TacticalPlan::decode);
    }

    private static Optional<TacticalPlan> plan(AiTurnContext context, String target) {
        return rememberedPlan(context)
                .filter(plan -> plan.target().equals(target))
                .or(() -> context.game().getTacticalActionDisplacement().isEmpty()
                        ? TacticalPlanner.forTarget(context.game(), context.seat(), target)
                        : Optional.empty());
    }

    private static AiDecision pickSystem(AiTurnContext context, List<AiPrompt> pickers, TacticalPlan plan) {
        Optional<Match> target =
                Prompts.first(pickers, button -> button.handlerId().equals(RING_TILE_PREFIX + plan.target()));
        if (target.isPresent()) return target.get().press("activate " + plan.target());
        AiPrompt picker = pickers.getFirst();
        String pressesKey = PICKER_PRESSES_KEY + context.turnKey();
        int presses = Integer.parseInt(context.memory().get(pressesKey).orElse("0"));
        Optional<PromptButton> navigate =
                presses >= MAX_PICKER_PRESSES ? Optional.empty() : navigationButton(context, picker, plan.target());
        if (navigate.isEmpty()) {
            return bestOffered(context, pickers)
                    .orElseGet(() -> new AiDecision.Unsure(picker, "it could not find system " + plan.target()));
        }
        context.memory().put(pressesKey, String.valueOf(presses + 1));
        return AiDecision.press(picker, navigate.get(), "look for system " + plan.target());
    }

    private static Optional<AiDecision> bestOffered(AiTurnContext context, List<AiPrompt> pickers) {
        Map<String, Match> offered = new LinkedHashMap<>();
        for (AiPrompt prompt : pickers) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.handlerId().startsWith(RING_TILE_PREFIX)) {
                    offered.putIfAbsent(
                            StringUtils.removeStart(button.handlerId(), RING_TILE_PREFIX), new Match(prompt, button));
                }
            }
        }
        Optional<TacticalPlan> best = offered.keySet().stream()
                .map(position -> TacticalPlanner.forTarget(context.game(), context.seat(), position))
                .flatMap(Optional::stream)
                .filter(plan -> plan.score() >= TacticalPlanner.MIN_SCORE)
                .max(Comparator.comparingDouble(TacticalPlan::score));
        if (best.isEmpty()) return Optional.empty();
        remember(context, best.get());
        return Optional.of(offered.get(best.get().target()).press("activate " + describe(best.get()) + " instead"));
    }

    private static boolean isPicker(AiPrompt prompt) {
        return prompt.hasHandlerPrefix("ringTile_")
                || prompt.hasHandlerPrefix("getTilesThisFarAway_")
                || prompt.hasHandlerPrefix("ring_");
    }

    private static Optional<PromptButton> navigationButton(AiTurnContext context, AiPrompt picker, String target) {
        Optional<PromptButton> ring = ringButtons(target).stream()
                .map(picker::enabledHandler)
                .flatMap(Optional::stream)
                .findFirst();
        if (ring.isPresent()) return ring;
        Integer distance = CheckDistanceHelper.getTileDistancesRelativeToAllYourUnlockedTiles(
                        context.game(), context.seat())
                .get(target);
        if (distance == null) return Optional.empty();
        return picker.enabledButtons().stream()
                .filter(button -> button.handlerId().startsWith("getTilesThisFarAway_"))
                .min(Comparator.comparingInt(button -> Math.abs(bandOf(button) - distance)));
    }

    static List<String> ringButtons(String position) {
        if (position.length() != 3 || !StringUtils.isNumeric(position)) return List.of("ring_corners");
        int ring = position.charAt(0) - '0';
        if (ring < LARGE_RING) return List.of("ring_" + ring);
        int index = Integer.parseInt(position.substring(1));
        String half = index <= ring * 6 / 2 ? "_right" : "_left";
        return List.of("ring_" + ring + half, "ring_" + ring);
    }

    private static int bandOf(PromptButton button) {
        String band = StringUtils.substringAfterLast(button.handlerId(), "_");
        return StringUtils.isNumeric(band) ? Integer.parseInt(band) : Integer.MAX_VALUE / 2;
    }

    private static Optional<AiDecision> movement(
            AiTurnContext context, List<AiPrompt> turn, String target, Optional<TacticalPlan> plan) {
        String faction = context.faction();
        Optional<AiPrompt> prompt = Prompts.newestWith(
                turn,
                button -> button.isOwnedBy(faction)
                        && (button.handlerId().startsWith("unitTacticalMove_")
                                || button.handlerId().startsWith("tacticalMoveFrom_")
                                || button.handlerId().startsWith("concludeMove_")));
        if (prompt.isEmpty()) return Optional.empty();
        AiPrompt movementPrompt = prompt.get();
        if (CeasefireRules.blocksMovement(context)) return holdPosition(context, turn, movementPrompt, target);
        if (movementPrompt.hasHandlerPrefix("unitTacticalMove_")) {
            return unitView(context, movementPrompt, plan);
        }
        if (plan.isPresent()) {
            for (UnitMove move : plan.get().moves()) {
                if (remaining(context, move) <= 0 || originExhausted(context, move.origin())) continue;
                Optional<Match> from = Prompts.owned(turn, faction, ("tacticalMoveFrom_" + move.origin())::equals);
                if (from.isPresent()) return Optional.of(from.get().press("pick units in " + move.origin()));
            }
        }
        return Prompts.owned(turn, faction, ("concludeMove_" + target)::equals)
                .map(match -> match.press("finish moving"));
    }

    private static Optional<AiDecision> holdPosition(
            AiTurnContext context, List<AiPrompt> turn, AiPrompt movementPrompt, String target) {
        Optional<PromptButton> doneHere =
                movementPrompt.firstEnabled(button -> button.handlerId().startsWith("doneWithOneSystem_"));
        if (doneHere.isPresent()) {
            return Optional.of(
                    AiDecision.press(movementPrompt, doneHere.get(), "stop picking units under a Ceasefire"));
        }
        String faction = context.faction();
        if (!context.game().getTacticalActionDisplacement().isEmpty()) {
            Optional<Match> reset = Prompts.owned(turn, faction, "resetTacticalMovement"::equals);
            if (reset.isPresent()) return Optional.of(reset.get().press("take back its moves under a Ceasefire"));
        }
        return Prompts.owned(turn, faction, ("concludeMove_" + target)::equals)
                .map(match -> match.press("move nothing into " + target + " under a Ceasefire"));
    }

    private static Optional<AiDecision> unitView(AiTurnContext context, AiPrompt prompt, Optional<TacticalPlan> plan) {
        Optional<String> origin = prompt.buttons().stream()
                .map(PromptButton::handlerId)
                .filter(id -> id.startsWith("doneWithOneSystem_"))
                .map(id -> StringUtils.substringAfter(id, "doneWithOneSystem_"))
                .findFirst();
        if (origin.isEmpty()) return Optional.empty();
        if (plan.isPresent()) {
            for (UnitMove move : plan.get().moves()) {
                if (!move.origin().equals(origin.get())) continue;
                int remaining = remaining(context, move);
                if (remaining <= 0) continue;
                Optional<PromptButton> button = moveButton(context, prompt, move, Math.min(2, remaining))
                        .or(() -> moveButton(context, prompt, move, 1));
                if (button.isPresent()) {
                    return Optional.of(AiDecision.press(prompt, button.get(), "move " + move.type()));
                }
            }
        }
        context.memory().put(EXHAUSTED_ORIGIN_KEY + context.turnKey() + "|" + origin.get(), "yes");
        return prompt.enabledHandler("doneWithOneSystem_" + origin.get())
                .map(button -> AiDecision.press(prompt, button, "done picking units in " + origin.get()));
    }

    private static boolean originExhausted(AiTurnContext context, String origin) {
        return context.memory().has(EXHAUSTED_ORIGIN_KEY + context.turnKey() + "|" + origin);
    }

    private static Optional<PromptButton> moveButton(
            AiTurnContext context, AiPrompt prompt, UnitMove move, int amount) {
        String asyncId = BoardView.model(context.seat(), move.type())
                .map(model -> model.getAsyncId())
                .orElse(move.type().value);
        String prefix = "unitTacticalMove_" + move.origin() + "_" + amount + "_" + asyncId + "_";
        String expectedTail = BoardView.SPACE.equals(move.holder())
                ? context.seat().getColor()
                : move.holder() + "_" + context.seat().getColor();
        return prompt.firstEnabled(button -> button.handlerId().startsWith(prefix)
                && matchesTail(button.handlerId().substring(prefix.length()), expectedTail));
    }

    static boolean matchesTail(String tail, String expectedTail) {
        if (tail.equals(expectedTail)) return true;
        if (!tail.endsWith("_" + expectedTail)) return false;
        return STATE_SEGMENTS.contains(tail.substring(0, tail.length() - expectedTail.length() - 1));
    }

    static int remaining(AiTurnContext context, UnitMove move) {
        Map<UnitKey, List<Integer>> staged = context.game()
                .getTacticalActionDisplacement()
                .getOrDefault(move.origin() + "-" + move.holder(), Map.of());
        int moved = staged.entrySet().stream()
                .filter(entry -> entry.getKey().unitType() == move.type())
                .filter(entry -> context.seat().getColor().equals(entry.getKey().getColor()))
                .mapToInt(entry ->
                        entry.getValue().stream().mapToInt(Integer::intValue).sum())
                .sum();
        return move.count() - moved;
    }

    private static Optional<AiDecision> landing(
            AiTurnContext context, List<AiPrompt> turn, Tile tile, Optional<TacticalPlan> plan) {
        String target = tile.getPosition();
        Optional<Match> done = Prompts.owned(turn, context.faction(), ("doneLanding_" + target)::equals);
        if (done.isEmpty()) return Optional.empty();
        Player seat = context.seat();
        if (BoardView.groundForces(BoardView.space(tile), seat) > 0) {
            Optional<Landing> landing = nextLanding(context, tile, plan)
                    .filter(found -> !needsCustodiansPayment(tile, found.planet())
                            || custodiansPayment(context).isPresent());
            Optional<PromptButton> button =
                    landing.flatMap(found -> landButton(done.get().prompt(), tile, found, seat));
            if (button.isPresent()) {
                expectCustodiansPayment(context, tile, landing.get().planet());
                return Optional.of(AiDecision.press(
                        done.get().prompt(),
                        button.get(),
                        "land on " + landing.get().planet()));
            }
        }
        return Optional.of(done.get().press("done landing"));
    }

    private static Optional<PromptButton> landButton(AiPrompt prompt, Tile tile, Landing landing, Player seat) {
        for (UnitType type : List.of(UnitType.Infantry, UnitType.Mech)) {
            int inSpace = BoardView.count(BoardView.space(tile), seat, type);
            if (inSpace == 0) continue;
            int amount = Math.min(2, Math.min(inSpace, landing.wanted()));
            Optional<PromptButton> button = landButton(prompt, tile.getPosition(), landing.planet(), amount, type, seat)
                    .or(() -> landButton(prompt, tile.getPosition(), landing.planet(), 1, type, seat));
            if (button.isPresent()) return button;
        }
        return Optional.empty();
    }

    private record Landing(String planet, int wanted) {}

    private static void expectCustodiansPayment(AiTurnContext context, Tile tile, String planetName) {
        if (!needsCustodiansPayment(tile, planetName)) return;
        custodiansPayment(context)
                .ifPresent(payment ->
                        PaymentRules.expect(context, "the custodians token", payment, PaymentRules.OBJECTIVE_DONE));
    }

    private static boolean needsCustodiansPayment(Tile tile, String planetName) {
        return tile.getUnitHolders().get(planetName) instanceof Planet planet && BoardView.hasCustodians(planet);
    }

    private static Optional<Wallet.Payment> custodiansPayment(AiTurnContext context) {
        SpendCost cost = SpendCost.influence(CUSTODIANS_COST);
        return ScoringReserve.planAfterReserve(context.game(), context.seat(), cost)
                .or(() -> Wallet.of(context.game(), context.seat()).plan(cost));
    }

    private static Optional<Landing> nextLanding(AiTurnContext context, Tile tile, Optional<TacticalPlan> plan) {
        Player seat = context.seat();
        if (plan.isPresent()) {
            for (Map.Entry<String, Integer> wanted : plan.get().landings().entrySet()) {
                Planet planet = tile.getUnitHolders().get(wanted.getKey()) instanceof Planet p ? p : null;
                if (planet == null) continue;
                int missing = wanted.getValue() - BoardView.groundForces(planet, seat);
                if (missing > 0) return Optional.of(new Landing(planet.getName(), missing));
            }
            return Optional.empty();
        }
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .filter(planet -> BoardView.groundForces(planet, seat) == 0)
                .filter(planet -> !BoardView.enemyStructuresOn(context.game(), seat, planet))
                .max(Comparator.comparingDouble(BoardView::planetValue))
                .map(planet -> new Landing(planet.getName(), 1));
    }

    private static Optional<PromptButton> landButton(
            AiPrompt prompt, String target, String planet, int amount, UnitType type, Player seat) {
        String id = "landUnits_" + target + "_" + amount + type.getValue() + "_" + planet + "_" + seat.getColor();
        return prompt.enabledHandler(id);
    }

    private static boolean groundCombatOngoing(Game game, Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet ->
                        BoardView.groundForces(planet, seat) > 0 && BoardView.enemyGroundForcesOn(game, seat, planet));
    }

    private static Optional<AiDecision> waitForCombat(AiTurnContext context, List<AiPrompt> turn) {
        boolean combatThread =
                context.prompts().stream().anyMatch(prompt -> prompt.source() == PromptSource.COMBAT_THREAD);
        if (combatThread) {
            return Optional.of(new AiDecision.Wait(context.now() + COMBAT_WAIT_MILLIS, "combat in the active system"));
        }
        String key = COMBAT_EXPECTED_KEY + context.turnKey();
        long since = context.memory().get(key).map(Long::parseLong).orElse(context.now());
        if (!context.memory().has(key)) context.memory().put(key, String.valueOf(since));
        if (context.now() - since < COMBAT_THREAD_GRACE_MILLIS) {
            return Optional.of(new AiDecision.Wait(since + COMBAT_THREAD_GRACE_MILLIS, "a combat to start"));
        }
        return turn.stream()
                .filter(prompt -> prompt.buttons().stream().anyMatch(button -> button.isOwnedBy(context.faction())))
                .findFirst()
                .map(prompt -> new AiDecision.Unsure(prompt, "its units share a system with another player's units"));
    }

    private static Optional<AiDecision> build(AiTurnContext context, List<AiPrompt> turn, Tile tile) {
        String faction = context.faction();
        Optional<Match> build = Prompts.owned(turn, faction, ("tacticalActionBuild_" + tile.getPosition())::equals);
        Optional<Match> conclude = Prompts.owned(turn, faction, "doneWithTacticalAction"::equals);
        if (build.isPresent()) {
            BuildPlan plan = ProductionPlanner.plan(context.game(), context.seat(), tile);
            if (!plan.isEmpty()) {
                context.memory().put(BUILD_KEY + context.turnKey(), plan.encode());
                return Optional.of(build.get().press("build units"));
            }
        }
        return conclude.map(match -> match.press("conclude the tactical action"));
    }

    public static Optional<AiDecision> placeUnits(
            AiTurnContext context, List<AiPrompt> prompts, String source, String target, Optional<BuildPlan> plan) {
        Optional<Match> done = Prompts.first(
                prompts,
                button -> button.isOwnedBy(context.faction())
                        && button.handlerId().equals(DONE_PREFIX + source + "_" + target)
                        && DONE_PRODUCING.equals(button.label()));
        if (done.isEmpty()) return Optional.empty();
        if (plan.isPresent()) {
            for (BuildOrder order : plan.get().orders()) {
                if (produced(context.seat(), order.type()) >= plan.get().units(order.type())) continue;
                AiPrompt prompt = done.get().prompt();
                Optional<PromptButton> button = prompt.enabledHandler(order.handlerId())
                        .or(() ->
                                order.units() > 1 ? prompt.enabledHandler(order.singleHandlerId()) : Optional.empty());
                if (button.isPresent()) {
                    return Optional.of(AiDecision.press(prompt, button.get(), "build " + order.unitId()));
                }
            }
        }
        return Optional.of(done.get().press("done building"));
    }

    private static int produced(Player seat, UnitType type) {
        String asyncId =
                BoardView.model(seat, type).map(model -> model.getAsyncId()).orElse(type.value);
        return seat.getCurrentProducedUnits().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(asyncId + "_"))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public static Optional<AiDecision> payForUnits(AiTurnContext context, List<AiPrompt> prompts, String source) {
        Game game = context.game();
        Player seat = context.seat();
        String cost = game.getStoredValue("producedUnitCostFor" + context.faction());
        if (StringUtils.isBlank(cost)) return Optional.empty();
        Optional<Match> done = Prompts.first(
                prompts,
                button -> button.isUnowned()
                        && (DONE_PREFIX + source).equals(button.handlerId())
                        && DONE_EXHAUSTING.equals(button.label()));
        if (done.isEmpty()) return Optional.empty();
        AiPrompt payment = done.get().prompt();
        double owed = parseDouble(cost) - spent(game, seat);
        if (owed > 0 && !seat.getSpentThingsThisWindow().contains(SARWEEN)) {
            Optional<PromptButton> sarween = payment.enabledHandler("useTech_st");
            if (sarween.isPresent()) return Optional.of(AiDecision.press(payment, sarween.get(), "use Sarween Tools"));
        }
        if (owed > 0) {
            Optional<PromptButton> planet = PaymentPlanner.keepingReserve(game, seat, payment, owed)
                    .or(() -> PaymentPlanner.choosePlanet(game, payment, owed));
            if (planet.isPresent()) return Optional.of(AiDecision.press(payment, planet.get(), "pay for units"));
            if (seat.getTg() > 0) {
                Optional<PromptButton> tradeGood = payment.enabledHandler("reduceTG_1_res");
                if (tradeGood.isPresent()) {
                    return Optional.of(AiDecision.press(payment, tradeGood.get(), "pay with a trade good"));
                }
            }
        }
        return Optional.of(done.get().press("done paying"));
    }

    private static double spent(Game game, Player seat) {
        double spent = 0;
        for (String thing : seat.getSpentThingsThisWindow()) {
            if (SARWEEN.equals(thing)) {
                spent += 1;
            } else if (thing.startsWith("tg_")) {
                spent += parseDouble(StringUtils.substringAfter(thing, "tg_"));
            } else if (seat.getPlanets().contains(thing)) {
                spent += BoardView.planetResources(game, thing);
            }
        }
        return spent;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String describe(TacticalPlan plan) {
        return plan.kind().name().toLowerCase() + " " + plan.target();
    }
}
