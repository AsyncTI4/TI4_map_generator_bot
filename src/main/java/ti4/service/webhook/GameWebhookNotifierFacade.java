package ti4.service.webhook;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;

@UtilityClass
public class GameWebhookNotifierFacade {

    private static final AtomicBoolean LOOKUP_FAILURE_LOGGED = new AtomicBoolean(false);

    public static void turnChanged(Game game) {
        GameWebhookNotifier notifier = getNotifier();
        if (notifier != null) {
            notifier.turnChanged(game);
        }
    }

    public static boolean phaseChanged(Game game, String previousPhaseOfGame) {
        GameWebhookNotifier notifier = getNotifier();
        return notifier != null && notifier.phaseChanged(game, previousPhaseOfGame);
    }

    public static boolean phaseChanged(Game game, String previousPhaseOfGame, String phaseOfGame) {
        GameWebhookNotifier notifier = getNotifier();
        return notifier != null && notifier.phaseChanged(game, previousPhaseOfGame, phaseOfGame);
    }

    private static GameWebhookNotifier getNotifier() {
        try {
            return SpringContext.getBean(GameWebhookNotifier.class);
        } catch (Exception e) {
            if (LOOKUP_FAILURE_LOGGED.compareAndSet(false, true)) {
                BotLogger.warning("Game webhook notifier is unavailable; webhook notifications will be skipped.", e);
            }
            return null;
        }
    }
}
