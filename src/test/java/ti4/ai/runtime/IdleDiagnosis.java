package ti4.ai.runtime;

import java.util.stream.Collectors;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

// One trace line for a seat that went idle on its own turn: the checks its turn logic exits on, its secret count
// against the limit, the Imperial draw queue, and every prompt it could see (this turn's marked with *).
final class IdleDiagnosis {

    private IdleDiagnosis() {}

    static String describe(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        var thisTurn = Prompts.thisTurn(context);
        String prompts = context.prompts().stream()
                .map(prompt -> (thisTurn.contains(prompt) ? "*" : "") + prompt.source() + handlers(prompt))
                .collect(Collectors.joining(" "));
        return String.format(
                "%s idle on its turn: inProgress=%s followsPending=%s awaitingSecondaries=%s actionTaken=%s"
                        + " pickingSystem=%s played=%s followed=%s secrets unscored %d scored %d max %d"
                        + " queue='%s' blockers='%s' prompts=%s",
                context.faction(),
                TacticalRules.inProgress(context),
                StrategyCardRules.followsPending(context),
                StrategyCardRules.awaitingSecondaries(context),
                TacticalRules.actionTaken(context),
                TacticalRules.pickingSystem(context),
                game.getPlayedSCs(),
                seat.getFollowedSCs(),
                seat.getSecretsUnscored().size(),
                seat.getSoScored(),
                seat.getMaxSOCount(),
                game.getStoredValue("queueToDrawSOs"),
                game.getStoredValue("potentialBlockers"),
                prompts);
    }

    private static String handlers(AiPrompt prompt) {
        return prompt.buttons().stream().map(PromptButton::customId).toList().toString();
    }
}
