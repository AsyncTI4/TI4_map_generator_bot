package ti4.ai.sol;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.strategy.StrategyTokenCost;
import ti4.ai.tactical.GroundDrops;
import ti4.ai.tactical.GroundDrops.Drop;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class OrbitalDropRules {

    public static final String PLANET_PREFIX = "placeOneNDone_skipbuildorbital_2gf_";
    public static final String MECH_PREFIX = "orbitalMechDrop_";
    public static final String FINISH = "finishComponentAction_spitItOut";
    static final String ABILITY = "orbital_drop";
    static final double TOKEN_VALUE = 2.0;
    static final double LOST_FOLLOW_VALUE = 1.5;
    static final double MIN_NET = 0.6;
    static final double MIN_MECH_NET = 0.5;
    private static final String ABILITY_BUTTON = "componentActionRes_ability_orbitalDrop";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String TURN_END = "turnEnd";
    private static final String KEY = "orbitalDrop|";
    private static final String FIELD = "~";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final int PLENTIFUL_SPARE = 3;
    private static final int COMFORTABLE_SPARE = 2;
    private static final double PLENTIFUL_SHARE = 0.4;
    private static final double COMFORTABLE_SHARE = 0.6;
    private static final double TIGHT_SHARE = 0.8;

    enum Stage {
        MENU,
        PLANET,
        FOLLOW_UP,
        DONE
    }

    record Flow(Stage stage, long since, String planet, boolean mech) {

        String encode() {
            return String.join(FIELD, stage.name(), String.valueOf(since), planet, String.valueOf(mech));
        }

        Flow at(Stage next, long now) {
            return new Flow(next, now, planet, mech);
        }

        static Optional<Flow> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 4 || !StringUtils.isNumeric(fields[1])) return Optional.empty();
            try {
                return Optional.of(new Flow(
                        Stage.valueOf(fields[0]),
                        Long.parseLong(fields[1]),
                        fields[2],
                        Boolean.parseBoolean(fields[3])));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    record Plan(String planet, boolean mech, double net) {}

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context)) return Optional.empty();
        Optional<TacticalPlan> tactical = tacticalPlan(context);
        Optional<Plan> plan = plan(context, tactical);
        if (plan.isEmpty()) return Optional.empty();
        if (tactical.isPresent() && tactical.get().score() >= plan.get().net()) return Optional.empty();
        return open(context, thisTurn, plan.get(), "instead of a weaker tactical action");
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context)) return Optional.empty();
        return plan(context, tacticalPlan(context))
                .flatMap(plan -> open(context, thisTurn, plan, "instead of passing"));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<Flow> flow = current(context);
        if (flow.isEmpty() || flow.get().stage() == Stage.DONE) return Optional.empty();
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= flow.get().since() - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<AiDecision> step =
                switch (flow.get().stage()) {
                    case MENU -> openAbility(context, fresh, flow.get());
                    case PLANET -> choosePlanet(context, fresh, flow.get());
                    case FOLLOW_UP -> followUp(context, fresh, flow.get());
                    case DONE -> Optional.empty();
                };
        if (step.isPresent()) return step;
        if (flow.get().stage() == Stage.FOLLOW_UP && turnCanEnd(context, fresh)) {
            remember(context, flow.get().at(Stage.DONE, context.now()));
            return Optional.empty();
        }
        long until = flow.get().since() + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "Orbital Drop"));
        remember(context, flow.get().at(Stage.DONE, context.now()));
        return Optional.empty();
    }

    static Optional<Plan> plan(AiTurnContext context, Optional<TacticalPlan> tactical) {
        Game game = context.game();
        Player seat = context.seat();
        OptionalDouble cost = tokenCost(game, seat);
        if (cost.isEmpty()) return Optional.empty();
        double stall = Stall.actionValue(game, seat);
        boolean mechPayable = mechPayable(game, seat);
        Plan best = null;
        for (Drop drop : GroundDrops.drops(game, seat, tactical)) {
            double mechNet = mechPayable ? mechNet(game, seat, drop) : 0;
            boolean withMech = mechNet >= MIN_MECH_NET;
            double net = drop.infantry() + (withMech ? mechNet : 0) + stall - cost.getAsDouble();
            if (best == null || net > best.net()) best = new Plan(drop.planet(), withMech, net);
        }
        return Optional.ofNullable(best).filter(found -> found.net() >= MIN_NET);
    }

    static double mechNet(Game game, Player seat, Drop drop) {
        double bodyPremium =
                (GroundDrops.MECH_RESOURCES - mechCost(seat)) * ProductionPlanner.fillerValuePerResource(game);
        return drop.mech() - bodyPremium;
    }

    static OptionalDouble tokenCost(Game game, Player seat) {
        if (!StrategyTokenCost.canSpare(game, seat)) return OptionalDouble.empty();
        int follows = StrategyCardRules.strategyTokensWantedForFollows(game, seat);
        int spare = seat.getStrategicCC() - 1 - follows;
        double lostFollow = spare < 0 ? LOST_FOLLOW_VALUE : 0;
        return OptionalDouble.of(TOKEN_VALUE * scarcity(spare) + lostFollow);
    }

    private static double scarcity(int spare) {
        if (spare >= PLENTIFUL_SPARE) return PLENTIFUL_SHARE;
        if (spare >= COMFORTABLE_SPARE) return COMFORTABLE_SHARE;
        return spare == 1 ? TIGHT_SHARE : 1.0;
    }

    private static boolean mechPayable(Game game, Player seat) {
        return seat.hasUnit("sol_mech")
                && ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(GroundDrops.MECH_RESOURCES))
                        .isPresent();
    }

    private static double mechCost(Player seat) {
        UnitModel mech = seat.getUnitByType(UnitType.Mech);
        return mech == null ? 0 : mech.getCost();
    }

    private static boolean canStart(AiTurnContext context) {
        Player seat = context.seat();
        Game game = context.game();
        return seat.hasAbility(ABILITY)
                && seat.getStrategicCC() > 0
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
        remember(context, new Flow(Stage.MENU, context.now(), plan.planet(), plan.mech()));
        return Optional.of(menu.get().press("use Orbital Drop for 2 infantry on " + plan.planet() + " " + when));
    }

    private static Optional<AiDecision> openAbility(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        Optional<Match> ability = Prompts.owned(fresh, context.faction(), ABILITY_BUTTON::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (ability.isEmpty()) return Optional.empty();
        remember(context, flow.at(Stage.PLANET, context.now()));
        return Optional.of(ability.get().press("use Orbital Drop"));
    }

    private static Optional<AiDecision> choosePlanet(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        String planned = PLANET_PREFIX + flow.planet();
        Optional<Match> planet = Prompts.owned(fresh, context.faction(), planned::equals)
                .or(() -> Prompts.owned(fresh, context.faction(), id -> id.startsWith(PLANET_PREFIX)))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (planet.isEmpty()) return Optional.empty();
        String chosen = StringUtils.removeStart(planet.get().button().handlerId(), PLANET_PREFIX);
        remember(
                context, new Flow(Stage.FOLLOW_UP, context.now(), chosen, flow.mech() && chosen.equals(flow.planet())));
        return Optional.of(planet.get().press("drop 2 infantry on " + chosen));
    }

    private static Optional<AiDecision> followUp(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        Optional<Match> mech = Prompts.unowned(fresh, (MECH_PREFIX + flow.planet())::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (flow.mech() && mech.isPresent()) return payForMech(context, flow, mech.get());
        Optional<Match> finish = Prompts.unowned(fresh, FINISH::equals)
                .filter(match -> match.button().label().startsWith("Finish"))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (finish.isEmpty()) return Optional.empty();
        remember(context, flow.at(Stage.DONE, context.now()));
        return Optional.of(finish.get().press("finish Orbital Drop without a mech"));
    }

    private static Optional<AiDecision> payForMech(AiTurnContext context, Flow flow, Match mech) {
        Optional<Wallet.Payment> payment = ScoringReserve.planAfterReserve(
                context.game(), context.seat(), SpendCost.resources(GroundDrops.MECH_RESOURCES));
        if (payment.isEmpty()) return Optional.empty();
        PaymentRules.expect(context, "the Orbital Drop mech", payment.get(), FINISH);
        remember(context, flow.at(Stage.DONE, context.now()));
        return Optional.of(mech.press("pay 3 resources to deploy a mech on " + flow.planet()));
    }

    private static boolean turnCanEnd(AiTurnContext context, List<AiPrompt> fresh) {
        return Prompts.owned(fresh, context.faction(), TURN_END::equals).isPresent();
    }

    static Optional<Flow> current(AiTurnContext context) {
        return context.memory().get(KEY + context.turnKey()).flatMap(Flow::decode);
    }

    private static void remember(AiTurnContext context, Flow flow) {
        context.memory().put(KEY + context.turnKey(), flow.encode());
    }
}
