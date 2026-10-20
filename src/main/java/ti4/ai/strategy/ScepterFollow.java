package ti4.ai.strategy;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;

@UtilityClass
class ScepterFollow {

    private static final String RELIC = "emelpar";
    private static final String BUTTON = "scepterE_follow_";
    private static final String KEY = "scepterFollow|";
    private static final String KEY_SEPARATOR = "|";

    static boolean pending(AiTurnContext context, int initiative) {
        return context.memory().has(key(context.game(), initiative));
    }

    static boolean covers(AiTurnContext context, StrategyCard card) {
        if (context.seat().hasRelicReady(RELIC)) return true;
        Game game = context.game();
        return game.getPlayedSCs().stream()
                .filter(initiative -> StrategyCard.of(game, initiative) == card)
                .anyMatch(initiative -> pending(context, initiative));
    }

    static void remember(AiTurnContext context, int initiative) {
        context.memory().put(key(context.game(), initiative), String.valueOf(context.now()));
    }

    static Optional<AiDecision> exhaustFirst(AiTurnContext context, int initiative, AiDecision decision) {
        if (!(decision instanceof AiDecision.Press)
                || !context.seat().hasRelicReady(RELIC)
                || pending(context, initiative)) {
            return Optional.empty();
        }
        String handler = BUTTON + initiative;
        Optional<Match> scepter = Prompts.unowned(Prompts.newestFirst(context.prompts()), handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        scepter.ifPresent(
                match -> context.memory().put(key(context.game(), initiative), String.valueOf(context.now())));
        return scepter.map(match -> match.press("follow with the Scepter of Emelpar instead of a strategy token"));
    }

    private static String key(Game game, int initiative) {
        return KEY + game.getRound() + KEY_SEPARATOR + initiative;
    }
}
