package ti4.ai.yin;

import java.util.ArrayList;
import java.util.List;
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
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.ai.yin.DannelPlan.Landing;
import ti4.ai.yin.DannelPlan.Plan;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

@UtilityClass
public class DannelRules {

    public static final String TARGET_PREFIX = "yinHeroTarget_";
    public static final String COMBAT_PREFIX = "startCombatOn_";
    static final String HERO_BUTTON = "componentActionRes_leader_" + DannelPlan.HERO;
    static final String START = "yinHeroStart";
    static final String PLANET_PREFIX = "yinHeroPlanet_";
    static final String INFANTRY_PREFIX = "yinHeroInfantry_";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String KEY = "dannel|";
    private static final String COMBAT_KEY = "dannelCombat|";
    private static final String FIELD = "~";
    private static final String ITEM = ";";
    private static final String PART = ":";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long COMBAT_HOLD_MILLIS = 15 * 60_000L;
    private static final long POLL_MILLIS = 10_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    enum Stage {
        MENU,
        HERO,
        TARGET,
        PLANET,
        COUNT,
        DONE
    }

    record Flow(Stage stage, long since, long started, int index, List<Landing> landings) {

        String encode() {
            String plan = landings.stream()
                    .map(landing ->
                            String.join(PART, landing.planet(), landing.owner(), String.valueOf(landing.infantry())))
                    .collect(Collectors.joining(ITEM));
            return String.join(
                    FIELD, stage.name(), String.valueOf(since), String.valueOf(started), String.valueOf(index), plan);
        }

        Flow at(Stage next, long now) {
            return new Flow(next, now, started, index, landings);
        }

        Flow landed(long now) {
            int nextIndex = index + 1;
            Stage next = nextIndex < landings.size() ? Stage.HERO : Stage.DONE;
            return new Flow(next, now, started, nextIndex, landings);
        }

        Landing current() {
            return landings.get(index);
        }

        List<Landing> landedSoFar() {
            return landings.subList(0, Math.min(index, landings.size()));
        }

        static Optional<Flow> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null
                    || fields.length != 5
                    || !StringUtils.isNumeric(fields[1])
                    || !StringUtils.isNumeric(fields[2])
                    || !StringUtils.isNumeric(fields[3])) {
                return Optional.empty();
            }
            List<Landing> landings = new ArrayList<>();
            for (String item : StringUtils.split(fields[4], ITEM)) {
                String[] parts = item.split(PART);
                if (parts.length != 3 || !StringUtils.isNumeric(parts[2])) return Optional.empty();
                landings.add(new Landing(parts[0], parts[1], Integer.parseInt(parts[2]), 0));
            }
            try {
                return Optional.of(new Flow(
                        Stage.valueOf(fields[0]),
                        Long.parseLong(fields[1]),
                        Long.parseLong(fields[2]),
                        Integer.parseInt(fields[3]),
                        landings));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context)) return Optional.empty();
        Optional<Plan> plan = DannelPlan.best(context.game(), context.seat());
        if (plan.isEmpty() || plan.get().net() <= 0) return Optional.empty();
        Optional<TacticalPlan> tactical = tacticalPlan(context);
        if (tactical.isPresent() && tactical.get().score() >= plan.get().net()) return Optional.empty();
        return open(context, thisTurn, plan.get(), "instead of a weaker tactical action");
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context)) return Optional.empty();
        return DannelPlan.best(context.game(), context.seat())
                .filter(plan -> plan.net() > 0)
                .flatMap(plan -> open(context, thisTurn, plan, "instead of passing"));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<Flow> flow = current(context);
        if (flow.isEmpty()) return Optional.empty();
        List<AiPrompt> sinceStart = since(context, flow.get().started());
        Optional<AiDecision> combat = startCombat(context, flow.get(), sinceStart);
        if (combat.isPresent() || flow.get().stage() == Stage.DONE) return combat;
        List<AiPrompt> fresh = since(context, flow.get().since());
        Optional<AiDecision> step =
                switch (flow.get().stage()) {
                    case MENU ->
                        owned(context, fresh, HERO_BUTTON)
                                .map(match -> advance(
                                        context,
                                        flow.get().at(Stage.HERO, context.now()),
                                        match,
                                        "play Dannel of the Tenth"));
                    case HERO ->
                        owned(context, sinceStart, START)
                                .map(match -> advance(
                                        context,
                                        flow.get().at(Stage.TARGET, context.now()),
                                        match,
                                        "invade with the Yin hero"));
                    case TARGET ->
                        Prompts.unowned(
                                        fresh,
                                        (TARGET_PREFIX + flow.get().current().owner())::equals)
                                .map(match -> advance(
                                        context,
                                        flow.get().at(Stage.PLANET, context.now()),
                                        match,
                                        "pick the owner of "
                                                + flow.get().current().planet()));
                    case PLANET ->
                        owned(
                                        context,
                                        fresh,
                                        PLANET_PREFIX + flow.get().current().planet())
                                .map(match -> advance(
                                        context,
                                        flow.get().at(Stage.COUNT, context.now()),
                                        match,
                                        "invade " + flow.get().current().planet()));
                    case COUNT -> landInfantry(context, flow.get(), fresh);
                    case DONE -> Optional.empty();
                };
        if (step.isPresent()) return step;
        long until = flow.get().since() + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "Dannel of the Tenth"));
        remember(context, flow.get().at(Stage.DONE, context.now()));
        return Optional.empty();
    }

    public static Optional<AiDecision> holdTurn(AiTurnContext context) {
        if (!context.isActivePlayer()) return Optional.empty();
        Optional<Flow> flow = current(context);
        if (flow.isEmpty() || flow.get().stage() != Stage.DONE) return Optional.empty();
        if (context.now() > flow.get().started() + COMBAT_HOLD_MILLIS) return Optional.empty();
        boolean fighting = flow.get().landings().stream().anyMatch(landing -> contested(context, landing.planet()));
        if (!fighting) return Optional.empty();
        return Optional.of(new AiDecision.Wait(context.now() + POLL_MILLIS, "its Dannel of the Tenth ground combats"));
    }

    private static Optional<AiDecision> landInfantry(AiTurnContext context, Flow flow, List<AiPrompt> fresh) {
        Landing landing = flow.current();
        Optional<Match> count = owned(context, fresh, INFANTRY_PREFIX + landing.planet() + "_" + landing.infantry());
        if (count.isEmpty()) return Optional.empty();
        Planet planet = context.game().getUnitHolderFromPlanet(landing.planet());
        if (planet != null && BoardView.hasCustodians(planet)) {
            ScoringReserve.planForCustodians(context.game(), context.seat())
                    .ifPresent(payment ->
                            PaymentRules.expect(context, "the custodians token", payment, PaymentRules.OBJECTIVE_DONE));
        }
        return Optional.of(advance(
                context,
                flow.landed(context.now()),
                count.get(),
                "land " + landing.infantry() + " infantry on " + landing.planet() + " with Dannel of the Tenth"));
    }

    private static Optional<AiDecision> startCombat(AiTurnContext context, Flow flow, List<AiPrompt> sinceStart) {
        for (Landing landing : flow.landedSoFar()) {
            String key = COMBAT_KEY + flow.started() + "|" + landing.planet();
            if (context.memory().has(key) || !contested(context, landing.planet())) continue;
            Optional<Match> start = sinceStart.stream()
                    .filter(prompt -> Prompts.mentions(prompt, context.seat()))
                    .map(prompt -> prompt.firstEnabled(button ->
                                    button.isUnowned() && (COMBAT_PREFIX + landing.planet()).equals(button.handlerId()))
                            .map(button -> new Match(prompt, button)))
                    .flatMap(Optional::stream)
                    .findFirst();
            if (start.isEmpty()) continue;
            context.memory().put(key, "pressed");
            return Optional.of(start.get().press("fight for " + landing.planet()));
        }
        return Optional.empty();
    }

    private static boolean contested(AiTurnContext context, String planetName) {
        Planet planet = context.game().getUnitHolderFromPlanet(planetName);
        return planet != null
                && BoardView.groundForces(planet, context.seat()) > 0
                && BoardView.enemyGroundForcesOn(context.game(), context.seat(), planet);
    }

    private static AiDecision advance(AiTurnContext context, Flow next, Match match, String reason) {
        remember(context, next);
        return match.press(reason);
    }

    private static Optional<Match> owned(AiTurnContext context, List<AiPrompt> prompts, String handler) {
        return Prompts.owned(prompts, context.faction(), handler::equals);
    }

    private static boolean canStart(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        return seat.hasLeaderUnlocked(DannelPlan.HERO)
                && context.isActivePlayer()
                && "action".equalsIgnoreCase(game.getPhaseOfGame())
                && !TacticalRules.inProgress(game, seat)
                && !TacticalRules.actionTaken(context)
                && current(context).isEmpty();
    }

    private static Optional<TacticalPlan> tacticalPlan(AiTurnContext context) {
        return TacticalRules.rememberedPlan(context).or(() -> {
            Optional<TacticalPlan> computed = TacticalPlanner.best(context.game(), context.seat());
            computed.ifPresent(found -> TacticalRules.remember(context, found));
            return computed;
        });
    }

    private static Optional<AiDecision> open(AiTurnContext context, List<AiPrompt> thisTurn, Plan plan, String when) {
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        remember(context, new Flow(Stage.MENU, context.now(), context.now(), 0, plan.landings()));
        String targets = plan.landings().stream().map(Landing::planet).collect(Collectors.joining(", "));
        return Optional.of(menu.get().press("play Dannel of the Tenth on " + targets + " " + when));
    }

    private static List<AiPrompt> since(AiTurnContext context, long since) {
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since - CLOCK_SKEW_MILLIS)
                .toList();
    }

    static Optional<Flow> current(AiTurnContext context) {
        return context.memory().get(KEY + context.turnKey()).flatMap(Flow::decode);
    }

    private static void remember(AiTurnContext context, Flow flow) {
        context.memory().put(KEY + context.turnKey(), flow.encode());
    }
}
