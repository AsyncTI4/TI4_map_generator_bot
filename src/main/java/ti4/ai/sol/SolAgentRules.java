package ti4.ai.sol;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;

@UtilityClass
public class SolAgentRules {

    public static final String START = "getAgentSelection_solagent";
    public static final String TARGET_PREFIX = "exhaustAgent_solagent_";
    private static final String AGENT = "solagent";
    private static final String KEY = "solAgent|";
    private static final String STARTED = "started";
    private static final String DONE = "done";
    private static final String DECLINED = "declined";
    private static final String FIELD = "~";
    private static final long TARGET_WAIT_MILLIS = 30_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static Optional<AiDecision> atRoundStart(AiTurnContext context, CombatMoment moment) {
        Player seat = context.seat();
        if (!moment.ground() || !seat.hasUnexhaustedLeader(AGENT)) return Optional.empty();
        String key = key(moment);
        Optional<String> state = context.memory().get(key);
        if (state.isPresent()) return awaitTarget(context, state.get());
        if (!fightsHere(context.game(), seat, moment)) return Optional.empty();
        if (!CombatForecast.extraDie(context.game(), seat, moment.position(), moment.holder())
                .worthSpending()) {
            context.memory().put(key, DECLINED);
            return Optional.empty();
        }
        Optional<Match> start = Prompts.owned(Prompts.newestFirst(context.prompts()), context.faction(), START::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (start.isEmpty()) return Optional.empty();
        context.memory().put(key, String.join(FIELD, STARTED, String.valueOf(context.now())));
        return Optional.of(start.get().press("use Evelyn Delouis for an extra die on " + moment.holder()));
    }

    public static Optional<AiDecision> chooseTarget(AiTurnContext context) {
        String own = TARGET_PREFIX + context.faction();
        for (String key : context.memory().keysStartingWith(KEY)) {
            Optional<Long> since = startedAt(context.memory().get(key).orElse(""));
            if (since.isEmpty()) continue;
            Optional<Match> target = Prompts.unowned(
                            Prompts.newestFirst(context.prompts()).stream()
                                    .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                                    .filter(prompt -> prompt.createdAtMillis() >= since.get() - CLOCK_SKEW_MILLIS)
                                    .toList(),
                            own::equals)
                    .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
            if (target.isPresent()) {
                context.memory().put(key, DONE);
                return Optional.of(target.get().press("give the extra die to its own ground forces"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> awaitTarget(AiTurnContext context, String state) {
        Optional<Long> since = startedAt(state);
        if (since.isEmpty()) return Optional.empty();
        long until = since.get() + TARGET_WAIT_MILLIS;
        if (context.now() >= until) return Optional.empty();
        return Optional.of(new AiDecision.Wait(until, "Evelyn Delouis' target"));
    }

    private static Optional<Long> startedAt(String state) {
        String[] fields = StringUtils.splitPreserveAllTokens(state, FIELD);
        if (fields == null || fields.length != 2 || !STARTED.equals(fields[0]) || !StringUtils.isNumeric(fields[1])) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(fields[1]));
    }

    private static boolean fightsHere(Game game, Player seat, CombatMoment moment) {
        Tile tile = game.getTileByPosition(moment.position());
        UnitHolder planet = tile == null ? null : tile.getUnitHolders().get(moment.holder());
        return planet != null
                && BoardView.groundForces(planet, seat) > 0
                && BoardView.enemyGroundForcesOn(game, seat, planet);
    }

    private static String key(CombatMoment moment) {
        return KEY + moment.position() + "@" + moment.holder() + "#" + moment.round();
    }
}
