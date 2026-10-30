package ti4.ai.legendary;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.legendary.LegendaryOutlook.HopesEnd;
import ti4.ai.legendary.LegendaryOutlook.Mallice;
import ti4.ai.legendary.LegendaryOutlook.Reinforcement;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.tactical.GroundDrops;
import ti4.ai.tactical.GroundDrops.Drop;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;

@UtilityClass
public class LegendaryRules {

    private static final String FLOW_KEY = "legendaryFlow|";
    private static final String USED_KEY = "legendaryUsed|";
    private static final String FIELD = "~";
    private static final String PLACE_PREFIX = "placeOneNDone_skipbuild_";
    public static final String MALLICE_PREFIX = "mallice_";
    public static final String EMELPAR_PREFIX = "emelparReady_planet_";
    private static final String MALLICE_TRADE_GOODS = MALLICE_PREFIX + "2_tg";
    private static final String MALLICE_CONVERT = MALLICE_PREFIX + "convert_comm";
    private static final String DRAW_ACTION_CARD = "draw_1_ACDelete";
    private static final String DONE_PREFIX = "deleteButtons";
    private static final String DONE_GAINING = "Done Gaining";
    private static final double MIN_NET = 0.1;
    private static final long STEP_WAIT_MILLIS = 45_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    private enum Stage {
        OPENED,
        TYPE_CHOSEN,
        TOKEN_GAINED
    }

    private record Flow(String planet, Stage stage, long since, String data) {

        String encode() {
            return String.join(FIELD, planet, stage.name(), String.valueOf(since), data);
        }

        Flow at(Stage next, long now, String newData) {
            return new Flow(planet, next, now, newData);
        }

        static Optional<Flow> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 4 || !StringUtils.isNumeric(fields[2])) return Optional.empty();
            try {
                return Optional.of(new Flow(fields[0], Stage.valueOf(fields[1]), Long.parseLong(fields[2]), fields[3]));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    private record Candidate(String planet, double net, Match ability) {}

    public static Optional<AiDecision> endOfTurn(AiTurnContext context, List<AiPrompt> thisTurn) {
        return open(context, thisTurn, LegendaryPlanets.AT_END_OF_TURN, "at the end of its turn");
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        return open(context, thisTurn, LegendaryPlanets.WHEN_PASSING, "when passing");
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<Flow> flow = flow(context);
        if (flow.isEmpty()) return Optional.empty();
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= flow.get().since() - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<AiDecision> step = resolve(context, fresh, flow.get());
        if (step.isPresent() || flow(context).isEmpty()) return step;
        long until = flow.get().since() + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "a legendary planet ability"));
        clear(context);
        return Optional.empty();
    }

    private static Optional<AiDecision> open(
            AiTurnContext context, List<AiPrompt> thisTurn, List<String> planets, String when) {
        if (!context.isActivePlayer() || flow(context).isPresent()) return Optional.empty();
        Player seat = context.seat();
        Candidate best = null;
        for (String planet : planets) {
            if (context.memory().has(usedKey(context, planet)) || !LegendaryPlanets.abilityReady(seat, planet)) {
                continue;
            }
            Optional<Match> ability = Prompts.owned(
                            thisTurn, context.faction(), LegendaryPlanets.button(planet)::equals)
                    .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
            if (ability.isEmpty()) continue;
            double net = LegendaryOutlook.value(context, planet);
            if (net >= MIN_NET && (best == null || net > best.net())) {
                best = new Candidate(planet, net, ability.get());
            }
        }
        if (best == null) return Optional.empty();
        begin(context, best.planet());
        return Optional.of(best.ability().press("use " + name(best.planet()) + " " + when));
    }

    private static void begin(AiTurnContext context, String planet) {
        context.memory().put(usedKey(context, planet), String.valueOf(context.now()));
        if (LegendaryPlanets.THUNDERS_EDGE.equals(planet)) {
            TacticalRules.allowAnotherAction(context);
            return;
        }
        remember(context, new Flow(planet, Stage.OPENED, context.now(), ""));
    }

    private static Optional<AiDecision> resolve(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        String planet = flow.planet();
        if (LegendaryPlanets.FLIGHT_ACADEMIES.contains(planet)) return placeFighters(context, fresh);
        if (LegendaryPlanets.ORDINIANS.contains(planet)) return gainToken(context, fresh, flow);
        return switch (planet) {
            case LegendaryPlanets.MALLICE -> mallice(context, fresh);
            case LegendaryPlanets.HOPES_END -> hopesEnd(context, fresh);
            case LegendaryPlanets.PRIMOR -> primor(context, fresh);
            case LegendaryPlanets.FAUNUS -> faunus(context, fresh);
            case LegendaryPlanets.EMELPAR -> emelpar(context, fresh);
            case LegendaryPlanets.INDUSTREX -> industrex(context, fresh, flow);
            default -> {
                clear(context);
                yield Optional.empty();
            }
        };
    }

    private static Optional<AiDecision> gainToken(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : fresh) {
            Optional<PromptButton> done = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && DONE_PREFIX.equals(button.handlerId())
                    && button.label().startsWith(DONE_GAINING));
            if (done.isEmpty() || context.alreadyPressed(prompt, done.get())) continue;
            Optional<PromptButton> grow = prompt.enabledHandler(CommandTokenPolicy.poolToGrow(game, seat));
            if (flow.stage() == Stage.OPENED && grow.isPresent()) {
                remember(context, flow.at(Stage.TOKEN_GAINED, context.now(), ""));
                return Optional.of(AiDecision.press(prompt, grow.get(), "gain a command token with Ordinian"));
            }
            clear(context);
            return Optional.of(AiDecision.press(prompt, done.get(), "finish gaining the command token"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> mallice(AiTurnContext context, List<AiPrompt> fresh) {
        Mallice choice = LegendaryOutlook.mallice(context.game(), context.seat());
        String handler = choice.convert() ? MALLICE_CONVERT : MALLICE_TRADE_GOODS;
        return finish(
                context,
                Prompts.unowned(fresh, handler::equals).filter(match -> notPressed(context, match)),
                choice.convert() ? "convert its commodities with Mallice" : "take 2 trade goods with Mallice");
    }

    private static Optional<AiDecision> hopesEnd(AiTurnContext context, List<AiPrompt> fresh) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<TacticalPlan> plan = TacticalRules.rememberedPlan(context);
        HopesEnd choice = LegendaryOutlook.hopesEnd(game, seat, plan);
        if (!choice.mech()) {
            return finish(
                    context,
                    Prompts.unowned(fresh, DRAW_ACTION_CARD::equals).filter(match -> notPressed(context, match)),
                    "draw an action card with Hope's End");
        }
        ToDoubleFunction<String> score = dropScore(GroundDrops.drops(game, seat, plan), Drop::mech);
        return finish(context, bestOwned(context, fresh, PLACE_PREFIX + "mech_", score), "drop a mech with Hope's End");
    }

    private static Optional<AiDecision> primor(AiTurnContext context, List<AiPrompt> fresh) {
        Game game = context.game();
        Player seat = context.seat();
        ToDoubleFunction<String> score =
                dropScore(GroundDrops.drops(game, seat, TacticalRules.rememberedPlan(context)), Drop::infantry);
        return finish(context, bestOwned(context, fresh, PLACE_PREFIX + "2gf_", score), "drop 2 infantry with Primor");
    }

    private static ToDoubleFunction<String> dropScore(List<Drop> drops, ToDoubleFunction<Drop> value) {
        return planet -> drops.stream()
                .filter(drop -> drop.planet().equals(planet))
                .mapToDouble(value)
                .findFirst()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    private static Optional<AiDecision> placeFighters(AiTurnContext context, List<AiPrompt> fresh) {
        Optional<String> position =
                LegendaryOutlook.fighters(context.game(), context.seat()).map(LegendaryOutlook.Placement::position);
        ToDoubleFunction<String> score = tile -> position.filter(tile::equals).isPresent() ? 1 : 0;
        return finish(
                context, bestOwned(context, fresh, PLACE_PREFIX + "2ff_", score), "put 2 fighters with its ships");
    }

    private static Optional<AiDecision> faunus(AiTurnContext context, List<AiPrompt> fresh) {
        Optional<String> planet =
                LegendaryOutlook.faunus(context.game(), context.seat()).map(LegendaryOutlook.Seizure::planet);
        if (planet.isEmpty()) {
            clear(context);
            return Optional.empty();
        }
        return finish(
                context,
                Prompts.owned(fresh, context.faction(), (LegendaryOutlook.FAUNUS_PREFIX + planet.get())::equals)
                        .filter(match -> notPressed(context, match)),
                "take control of " + planet.get() + " with Faunus");
    }

    private static Optional<AiDecision> emelpar(AiTurnContext context, List<AiPrompt> fresh) {
        Player seat = context.seat();
        Optional<String> planet = LegendaryOutlook.emelpar(context.game(), seat).map(ReadyingValue.Pick::planet);
        if (planet.isEmpty()) {
            clear(context);
            return Optional.empty();
        }
        String handler = EMELPAR_PREFIX + seat.getFaction() + "_" + planet.get();
        return finish(
                context,
                Prompts.unowned(fresh, handler::equals).filter(match -> notPressed(context, match)),
                "ready " + planet.get() + " with Emelpar");
    }

    private static Optional<AiDecision> industrex(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Reinforcement> plan = LegendaryOutlook.industrex(game, seat);
        if (plan.isEmpty()) {
            clear(context);
            return Optional.empty();
        }
        if (flow.stage() == Stage.OPENED) {
            String handler = LegendaryOutlook.INDUSTREX_PREFIX + plan.get().type().value;
            Optional<Match> pick = Prompts.owned(fresh, context.faction(), handler::equals)
                    .filter(match -> notPressed(context, match));
            pick.ifPresent(match -> remember(
                    context,
                    flow.at(Stage.TYPE_CHOSEN, context.now(), plan.get().type().value)));
            return pick.map(match -> match.press("choose the ship type to place with Industrex"));
        }
        String type = LegendaryOutlook.unitTypeOf(flow.data())
                .map(UnitType::plainName)
                .orElse("");
        String handler = PLACE_PREFIX + type + "_" + plan.get().position();
        return finish(
                context,
                Prompts.owned(fresh, context.faction(), handler::equals).filter(match -> notPressed(context, match)),
                "place the ship in " + plan.get().position());
    }

    private static Optional<Match> bestOwned(
            AiTurnContext context, List<AiPrompt> fresh, String prefix, ToDoubleFunction<String> score) {
        Optional<Match> best = Optional.empty();
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AiPrompt prompt : fresh) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.isOwnedBy(context.faction()) || !button.handlerId().startsWith(prefix)) continue;
                if (context.alreadyPressed(prompt, button)) continue;
                double value = score.applyAsDouble(StringUtils.removeStart(button.handlerId(), prefix));
                if (best.isEmpty() || value > bestScore) {
                    best = Optional.of(new Match(prompt, button));
                    bestScore = value;
                }
            }
        }
        return best;
    }

    private static Optional<AiDecision> finish(AiTurnContext context, Optional<Match> button, String reason) {
        button.ifPresent(match -> clear(context));
        return button.map(match -> match.press(reason));
    }

    private static boolean notPressed(AiTurnContext context, Match match) {
        return !context.alreadyPressed(match.prompt(), match.button());
    }

    private static String name(String planet) {
        return Mapper.getPlanet(planet).getName();
    }

    private static String usedKey(AiTurnContext context, String planet) {
        return USED_KEY + context.turnKey() + "|" + planet;
    }

    private static Optional<Flow> flow(AiTurnContext context) {
        return context.memory().get(FLOW_KEY + context.turnKey()).flatMap(Flow::decode);
    }

    private static void remember(AiTurnContext context, Flow flow) {
        context.memory().put(FLOW_KEY + context.turnKey(), flow.encode());
    }

    private static void clear(AiTurnContext context) {
        context.memory().remove(FLOW_KEY + context.turnKey());
    }
}
