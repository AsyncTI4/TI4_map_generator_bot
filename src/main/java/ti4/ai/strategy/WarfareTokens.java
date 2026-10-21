package ti4.ai.strategy;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class WarfareTokens {

    private static final String OPEN_STEP = "warfareRedistribute";
    private static final String DONE_STEP = "warfareRedistributed";
    private static final String SINCE_KEY = "warfareRedistributeSince|";
    private static final String MOVES_KEY = "warfareRedistributeMoves|";
    private static final String REDISTRIBUTE = "redistributeCCButtons";
    private static final String BEGIN = "beginTacticalTeWarfare";
    private static final String DONE_LABEL = "Done Redistributing";
    private static final long WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    static Optional<AiDecision> rebalance(AiTurnContext context) {
        if (CardMessages.done(context, DONE_STEP)) return Optional.empty();
        if (!CardMessages.done(context, OPEN_STEP)) return open(context);
        return adjust(context);
    }

    private static Optional<AiDecision> open(AiTurnContext context) {
        Optional<String> surplus = CommandTokenPolicy.midRoundSurplus(context.game(), context.seat());
        if (surplus.isEmpty()) return finish(context);
        Optional<Match> redistribute = Prompts.owned(
                Prompts.thisTurn(context), context.faction(), handler -> handler.startsWith(REDISTRIBUTE));
        if (redistribute.isEmpty()) {
            boolean beginOffered = Prompts.owned(Prompts.thisTurn(context), context.faction(), BEGIN::equals)
                    .isPresent();
            return beginOffered ? finish(context) : waitOrFinish(context, "the Warfare prompt");
        }
        CardMessages.markDone(context, OPEN_STEP);
        context.memory().put(SINCE_KEY + context.turnKey(), String.valueOf(context.now()));
        return Optional.of(redistribute
                .get()
                .press("move " + poolName(surplus.get())
                        + " command tokens the round plan does not need into the pools it does"));
    }

    private static Optional<AiDecision> adjust(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        long since = since(context);
        for (AiPrompt prompt : CardMessages.hiddenSince(context, since - CLOCK_SKEW_MILLIS)) {
            Optional<PromptButton> done = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && button.handlerId().startsWith("deleteButtons")
                    && button.label().startsWith(DONE_LABEL));
            if (done.isEmpty()) continue;
            int moves = moves(context);
            int netGain = CommandTokenPolicy.netGainSoFar(game, seat);
            if (netGain < 0) {
                Optional<PromptButton> grow = prompt.enabledHandler(CommandTokenPolicy.poolToRefill(game, seat));
                if (grow.isPresent()) return step(context, prompt, grow.get(), moves, "gain a command token");
            }
            boolean withinBudget = moves < 2 * (totalTokens(seat) - netGain);
            Optional<PromptButton> shrink = withinBudget
                    ? CommandTokenPolicy.midRoundSurplus(game, seat).flatMap(prompt::enabledHandler)
                    : Optional.empty();
            if (shrink.isPresent()) return step(context, prompt, shrink.get(), moves, "move a surplus command token");
            CardMessages.markDone(context, DONE_STEP);
            return Optional.of(AiDecision.press(prompt, done.get(), "finish redistributing command tokens"));
        }
        return waitOrFinish(context, "the command token prompt");
    }

    private static Optional<AiDecision> waitOrFinish(AiTurnContext context, String what) {
        String key = SINCE_KEY + context.turnKey();
        if (!context.memory().has(key)) context.memory().put(key, String.valueOf(context.now()));
        long until = since(context) + WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, what));
        return finish(context);
    }

    private static Optional<AiDecision> step(
            AiTurnContext context, AiPrompt prompt, PromptButton button, int moves, String reason) {
        context.memory().put(MOVES_KEY + context.turnKey(), String.valueOf(moves + 1));
        return Optional.of(AiDecision.press(prompt, button, reason));
    }

    private static Optional<AiDecision> finish(AiTurnContext context) {
        CardMessages.markDone(context, DONE_STEP);
        return Optional.empty();
    }

    private static long since(AiTurnContext context) {
        return context.memory()
                .get(SINCE_KEY + context.turnKey())
                .filter(StringUtils::isNumeric)
                .map(Long::parseLong)
                .orElse(context.now());
    }

    private static int moves(AiTurnContext context) {
        return context.memory()
                .get(MOVES_KEY + context.turnKey())
                .filter(StringUtils::isNumeric)
                .map(Integer::parseInt)
                .orElse(0);
    }

    private static int totalTokens(Player seat) {
        return seat.getTacticalCC() + seat.getFleetCC() + seat.getStrategicCC();
    }

    private static String poolName(String shrinkHandler) {
        return StringUtils.substringBetween(shrinkHandler, "decrease_", "_cc");
    }
}
