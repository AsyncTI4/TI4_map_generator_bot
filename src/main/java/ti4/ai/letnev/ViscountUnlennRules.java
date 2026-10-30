package ti4.ai.letnev;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Player;

@UtilityClass
public class ViscountUnlennRules {

    public static final String AGENT = "letnevagent";
    public static final String TARGET_PREFIX = "exhaustAgent_" + AGENT + "_";
    static final String OPEN = "getAgentSelection_" + AGENT;
    private static final String KEY = "letnevAgent|";
    private static final String SELECTING = "selecting";
    private static final String DECIDED = "decided";
    private static final String FIELD = "~";
    private static final long STEP_WAIT_MILLIS = 30_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static boolean ownsReadyAgent(Player seat) {
        return seat.hasLeader(AGENT) && seat.hasUnexhaustedLeader(AGENT);
    }

    public static Optional<AiDecision> atRoundStart(AiTurnContext context, CombatMoment moment) {
        if (moment.ground()) return Optional.empty();
        String key = KEY + moment.position() + "|" + moment.round();
        String[] state =
                StringUtils.splitPreserveAllTokens(context.memory().get(key).orElse(""), FIELD);
        if (state != null && state.length == 2 && SELECTING.equals(state[0]) && StringUtils.isNumeric(state[1])) {
            return chooseTarget(context, key, Long.parseLong(state[1]));
        }
        if (context.memory().has(key) || !ownsReadyAgent(context.seat())) return Optional.empty();
        Optional<Match> open = openButton(context);
        if (open.isEmpty()) return Optional.empty();
        context.memory().put(key, SELECTING + FIELD + context.now());
        return Optional.of(open.get().press("use Viscount Unlenn to give a ship an extra die"));
    }

    private static Optional<AiDecision> chooseTarget(AiTurnContext context, String key, long since) {
        String self = TARGET_PREFIX + context.faction();
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<Match> target = Prompts.unowned(fresh, self::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (target.isPresent()) {
            context.memory().put(key, DECIDED);
            return Optional.of(target.get().press("give its own ship the extra die"));
        }
        long until = since + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "Viscount Unlenn's target"));
        context.memory().put(key, DECIDED);
        return Optional.empty();
    }

    private static Optional<Match> openButton(AiTurnContext context) {
        List<AiPrompt> combat = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .toList();
        return Prompts.owned(combat, context.faction(), OPEN::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
    }
}
