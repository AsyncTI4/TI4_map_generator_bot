package ti4.ai.agenda;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.nekro.NekroRules;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class RiderPayouts {

    private static final String GET_A_TECHNOLOGY = "acquireATech";
    private static final String GAIN_TOKENS_TEXT = "Use buttons to gain command tokens";
    private static final String LEADERSHIP_KEY = "leadershipRiderGain|";
    private static final String RESOLVED_OUTCOME = "resolvedAgendaOutcome";
    private static final int LEADERSHIP_TOKENS = 3;
    private static final long RECENT_MILLIS = 30 * 60_000L;

    public static Optional<AiDecision> technology(AiTurnContext context) {
        Player seat = context.seat();
        if (StrategyCardRules.cannotResearch(seat)) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!isRecentPublicMessageNaming(context, prompt, RiderPayoff.TECHNOLOGY)) continue;
            Optional<PromptButton> research =
                    prompt.firstEnabled(button -> button.isUnowned() && GET_A_TECHNOLOGY.equals(button.handlerId()));
            if (research.isEmpty() || context.alreadyPressed(prompt, research.get())) continue;
            StrategyCardRules.expectResearch(context, 0);
            return Optional.of(AiDecision.press(prompt, research.get(), "research the technology its rider won"));
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> leadership(AiTurnContext context) {
        if (!wonRider(context.game(), context.seat(), RiderPayoff.LEADERSHIP)) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || context.now() - prompt.createdAtMillis() > RECENT_MILLIS) continue;
            Optional<Integer> target = leadershipTarget(context, prompt);
            if (target.isEmpty()) continue;
            Optional<AiDecision> gain = NekroRules.gainTokens(
                    context, prompt, target.get(), LEADERSHIP_KEY + prompt.messageId(), "its Leadership Rider");
            if (gain.isPresent()) return gain;
        }
        return Optional.empty();
    }

    private static Optional<Integer> leadershipTarget(AiTurnContext context, AiPrompt prompt) {
        String key = LEADERSHIP_KEY + prompt.messageId();
        Optional<Integer> remembered =
                context.memory().get(key).filter(StringUtils::isNumeric).map(Integer::parseInt);
        if (remembered.isPresent()) return remembered;
        if (!prompt.content().contains(GAIN_TOKENS_TEXT) || !Prompts.mentions(prompt, context.seat())) {
            return Optional.empty();
        }
        int target = Math.min(
                LEADERSHIP_TOKENS, CommandTokenPolicy.gainWithinReinforcements(context.game(), context.seat()));
        context.memory().put(key, String.valueOf(target));
        return Optional.of(target);
    }

    private static boolean isRecentPublicMessageNaming(AiTurnContext context, AiPrompt prompt, String rider) {
        return !prompt.isHidden()
                && context.now() - prompt.createdAtMillis() <= RECENT_MILLIS
                && prompt.content().contains(rider)
                && Prompts.mentions(prompt, context.seat());
    }

    static boolean wonRider(Game game, Player seat, String rider) {
        String outcome = game.getStoredValue(RESOLVED_OUTCOME);
        if (outcome.isEmpty()) return false;
        for (Map.Entry<String, String> votes : game.getCurrentAgendaVotes().entrySet()) {
            if (!votes.getKey().equalsIgnoreCase(outcome)) continue;
            for (String piece : votes.getValue().split(";")) {
                if (piece.equalsIgnoreCase(seat.getFaction() + "_" + rider)) return true;
            }
        }
        return false;
    }
}
