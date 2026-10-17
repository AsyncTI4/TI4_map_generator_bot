package ti4.ai.sol;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.GameHorizon;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.FreedPlays;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SolHeroRules {

    static final String HERO = "solhero";
    static final int FINAL_ROUNDS_LEFT = 1;
    static final double MIN_DECISIVE_POINTS = 1.0;
    private static final String HERO_BUTTON = "componentActionRes_leader_" + HERO;
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String TURN_END = "turnEnd";
    private static final String KEY = "solHero|";
    private static final String MENU = "menu";
    private static final String DONE = "done";
    private static final String FIELD = "~";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!worthPlaying(context)) return Optional.empty();
        return open(context, thisTurn, "to reactivate systems its command tokens block");
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!worthPlaying(context)) return Optional.empty();
        return open(context, thisTurn, "instead of passing");
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<String[]> state = state(context);
        if (state.isEmpty() || !MENU.equals(state.get()[0])) return Optional.empty();
        long since = Long.parseLong(state.get()[1]);
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<Match> hero = Prompts.owned(fresh, context.faction(), HERO_BUTTON::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (hero.isPresent()) {
            remember(context, DONE, context.now());
            return Optional.of(hero.get().press("play Jace X, 4th Air Legion to free its command tokens"));
        }
        if (Prompts.owned(fresh, context.faction(), TURN_END::equals).isPresent()
                || context.now() >= since + STEP_WAIT_MILLIS) {
            remember(context, DONE, context.now());
            return Optional.empty();
        }
        return Optional.of(new AiDecision.Wait(since + STEP_WAIT_MILLIS, "Jace X, 4th Air Legion"));
    }

    static boolean worthPlaying(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.hasLeaderUnlocked(HERO) || !canStart(context)) return false;
        if (seat.getTacticalCC() < 1) return false;
        if (game.getTileMap().values().stream().noneMatch(tile -> tile.hasPlayerCC(seat))) return false;
        FreedPlays.Gain gain = FreedPlays.gain(game, seat);
        if (gain.points() < MIN_DECISIVE_POINTS) return false;
        return isFinalRound(game) || gain.winsGame() || gain.stopsLeader();
    }

    static boolean isFinalRound(Game game) {
        return GameHorizon.roundsLeft(game) <= FINAL_ROUNDS_LEFT;
    }

    private static boolean canStart(AiTurnContext context) {
        return context.isActivePlayer()
                && "action".equalsIgnoreCase(context.game().getPhaseOfGame())
                && !TacticalRules.inProgress(context.game(), context.seat())
                && !TacticalRules.actionTaken(context)
                && state(context).isEmpty();
    }

    private static Optional<AiDecision> open(AiTurnContext context, List<AiPrompt> thisTurn, String when) {
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        remember(context, MENU, context.now());
        return Optional.of(menu.get().press("play its hero " + when));
    }

    private static Optional<String[]> state(AiTurnContext context) {
        return context.memory()
                .get(KEY + context.turnKey())
                .map(value -> StringUtils.splitPreserveAllTokens(value, FIELD))
                .filter(fields -> fields.length == 2 && StringUtils.isNumeric(fields[1]));
    }

    private static void remember(AiTurnContext context, String stage, long since) {
        context.memory().put(KEY + context.turnKey(), stage + FIELD + since);
    }
}
