package ti4.ai.xxcha;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class XxchaAgentRules {

    public static final String AGENT = "xxchaagent";
    public static final String USER_PREFIX = "exhaustAgent_" + AGENT + "_";
    public static final String READY_PREFIX = "khraskHeroStep4Ready_";
    private static final String KEY = "xxchaAgent|";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String AGENT_BUTTON = "componentActionRes_leader_" + AGENT;
    private static final String FIELD = "~";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final double MIN_VALUE = 1.0;

    enum Stage {
        MENU,
        LEADER,
        USER,
        DONE
    }

    record Flow(Stage stage, long since, String faction, String planet) {

        String encode() {
            return String.join(FIELD, stage.name(), String.valueOf(since), faction, planet);
        }

        Flow at(Stage next, long now) {
            return new Flow(next, now, faction, planet);
        }

        static Optional<Flow> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 4 || !StringUtils.isNumeric(fields[1])) return Optional.empty();
            try {
                return Optional.of(new Flow(Stage.valueOf(fields[0]), Long.parseLong(fields[1]), fields[2], fields[3]));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        Player seat = context.seat();
        Game game = context.game();
        if (!canStart(context)) return Optional.empty();
        Optional<ReadyingValue.Pick> pick = ReadyingValue.bestSingle(game, seat, exhaustedPlanets(seat));
        if (pick.isEmpty() || pick.get().value() < MIN_VALUE) return Optional.empty();
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        remember(
                context,
                new Flow(
                        Stage.MENU, context.now(), seat.getFaction(), pick.get().planet()));
        return Optional.of(menu.get().press("ready " + pick.get().planet() + " with Ggrocuto Rinn instead of passing"));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<Flow> flow = current(context);
        if (flow.isEmpty() || flow.get().stage() == Stage.DONE) return Optional.empty();
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= flow.get().since() - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<AiDecision> step =
                switch (flow.get().stage()) {
                    case MENU -> openAgent(context, fresh, flow.get());
                    case LEADER -> chooseUser(context, fresh, flow.get());
                    case USER -> chooseExhaustedPlanet(context, fresh, flow.get());
                    case DONE -> Optional.empty();
                };
        if (step.isPresent()) return step;
        long until = flow.get().since() + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "Ggrocuto Rinn"));
        remember(context, flow.get().at(Stage.DONE, context.now()));
        return Optional.empty();
    }

    public static boolean ownsReadyAgent(Player seat) {
        return seat.hasLeader(AGENT) && seat.hasUnexhaustedLeader(AGENT);
    }

    private static boolean canStart(AiTurnContext context) {
        Player seat = context.seat();
        Game game = context.game();
        return ownsReadyAgent(seat)
                && !game.isFowMode()
                && context.isActivePlayer()
                && "action".equalsIgnoreCase(game.getPhaseOfGame())
                && !TacticalRules.inProgress(game, seat)
                && !TacticalRules.actionTaken(context)
                && current(context).isEmpty();
    }

    private static Optional<AiDecision> openAgent(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        Optional<Match> agent = Prompts.owned(fresh, context.faction(), AGENT_BUTTON::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (agent.isEmpty()) return Optional.empty();
        remember(context, flow.at(Stage.LEADER, context.now()));
        return Optional.of(agent.get().press("use Ggrocuto Rinn"));
    }

    private static Optional<AiDecision> chooseUser(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        String handler = USER_PREFIX + flow.faction();
        Optional<Match> user = Prompts.unowned(fresh, handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (user.isEmpty()) return Optional.empty();
        remember(context, flow.at(Stage.USER, context.now()));
        return Optional.of(user.get().press("ready a planet of " + flow.faction()));
    }

    private static Optional<AiDecision> chooseExhaustedPlanet(AiTurnContext context, List<AiPrompt> fresh, Flow flow) {
        String handler = READY_PREFIX + flow.faction() + "_" + flow.planet();
        Optional<Match> ready = Prompts.unowned(fresh, handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (ready.isEmpty()) return Optional.empty();
        remember(context, flow.at(Stage.DONE, context.now()));
        return Optional.of(ready.get().press("ready " + flow.planet() + " with Ggrocuto Rinn"));
    }

    private static List<String> exhaustedPlanets(Player seat) {
        return seat.getExhaustedPlanets().stream()
                .filter(seat.getPlanets()::contains)
                .toList();
    }

    private static Optional<Flow> current(AiTurnContext context) {
        return context.memory().get(KEY + context.turnKey()).flatMap(Flow::decode);
    }

    private static void remember(AiTurnContext context, Flow flow) {
        context.memory().put(KEY + context.turnKey(), flow.encode());
    }
}
