package ti4.ai.promissory;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.GameHorizon;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.ai.trade.ConvoysValue;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ConvoysRules {

    static final double PER_NEW_PARTNER = 1.0;
    static final int ROUNDS_COUNTED = 2;
    private static final String KEY = "convoysPlay|";
    private static final String DONE = "done";
    private static final String MENU = "componentAction";
    private static final String PLAY_PREFIX = "componentActionRes_pn_";

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        Optional<String> note = playable(context);
        if (note.isEmpty()) return Optional.empty();
        double value = value(context.game(), context.seat());
        if (tacticalActionBeats(context, value + Stall.value(context.game(), context.seat()))) return Optional.empty();
        return open(context, thisTurn, note.get(), "play Trade Convoys instead of a weak tactical action");
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        return playable(context)
                .flatMap(note -> open(context, thisTurn, note, "play Trade Convoys as its last action before passing"));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        String key = KEY + context.turnKey();
        Optional<String> note = context.memory().get(key).filter(value -> !DONE.equals(value));
        if (note.isEmpty()) return Optional.empty();
        String handler = PLAY_PREFIX + note.get();
        Optional<Match> play = Prompts.owned(Prompts.newestFirst(context.prompts()), context.faction(), handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        play.ifPresent(ignored -> context.memory().put(key, DONE));
        return play.map(match -> match.press("play Trade Convoys to trade with players who are not its neighbours"));
    }

    static double value(Game game, Player seat) {
        if (!AiSettings.isTradingEnabled()) return 0;
        int rounds = Math.min(ROUNDS_COUNTED, GameHorizon.roundsLeft(game));
        return PER_NEW_PARTNER * ConvoysValue.newPartners(game, seat) * rounds;
    }

    static Optional<String> heldConvoys(Player seat) {
        return seat.getPromissoryNotes().keySet().stream()
                .filter(ConvoysValue::isConvoys)
                .filter(alias -> NoteHand.holdsForeign(seat, alias))
                .findFirst();
    }

    private static Optional<String> playable(AiTurnContext context) {
        if (context.memory().has(KEY + context.turnKey()) || TacticalRules.actionTaken(context)) {
            return Optional.empty();
        }
        if (value(context.game(), context.seat()) <= 0) return Optional.empty();
        return heldConvoys(context.seat());
    }

    private static Optional<AiDecision> open(
            AiTurnContext context, List<AiPrompt> thisTurn, String note, String reason) {
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), MENU::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        menu.ifPresent(ignored -> context.memory().put(KEY + context.turnKey(), note));
        return menu.map(match -> match.press(reason));
    }

    private static boolean tacticalActionBeats(AiTurnContext context, double actionValue) {
        Optional<TacticalPlan> plan = TacticalRules.rememberedPlan(context).or(() -> {
            Optional<TacticalPlan> computed = TacticalPlanner.best(context.game(), context.seat());
            computed.ifPresent(found -> TacticalRules.remember(context, found));
            return computed;
        });
        return plan.isPresent() && plan.get().score() >= actionValue;
    }
}
