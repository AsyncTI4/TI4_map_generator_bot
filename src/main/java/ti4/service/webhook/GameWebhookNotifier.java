package ti4.service.webhook;

import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.service.webhook.GameWebhookSubscriptionService;
import ti4.spring.service.webhook.WebhookUserEntity;

@Service
public class GameWebhookNotifier {

    private final GameWebhookSubscriptionService subscriptionService;
    private final WebhookDispatchService dispatchService;

    public GameWebhookNotifier(GameWebhookSubscriptionService subscriptionService, WebhookDispatchService dispatchService) {
        this.subscriptionService = subscriptionService;
        this.dispatchService = dispatchService;
    }

    public void turnChanged(Game game) {
        if (game == null) return;
        String phaseOfGame = normalizePhase(game.getPhaseOfGame());
        if (phaseOfGame == null) return;

        Player activePlayer = game.getActivePlayer();
        GameWebhookPayload payload = new GameWebhookPayload(
                game.getName(),
                GameWebhookEventType.TURN_CHANGED,
                null,
                phaseOfGame,
                game.getRound(),
                activePlayer == null ? null : activePlayer.getUserID(),
                activePlayer == null ? null : activePlayer.getFaction(),
                Instant.now());
        dispatchToSubscribers(game.getName(), GameWebhookEventType.TURN_CHANGED, payload);
    }

    public boolean phaseChanged(Game game, String previousPhaseOfGame) {
        if (game == null) return false;
        return phaseChanged(game, previousPhaseOfGame, game.getPhaseOfGame());
    }

    public boolean phaseChanged(Game game, String previousPhaseOfGame, String phaseOfGame) {
        if (game == null) return false;
        String previous = normalizePhase(previousPhaseOfGame);
        String current = normalizePhase(phaseOfGame);
        if (current == null || current.equals(previous)) return false;

        GameWebhookPayload payload = new GameWebhookPayload(
                game.getName(),
                GameWebhookEventType.PHASE_CHANGED,
                previous,
                current,
                game.getRound(),
                null,
                null,
                Instant.now());
        dispatchToSubscribers(game.getName(), GameWebhookEventType.PHASE_CHANGED, payload);
        return true;
    }

    private void dispatchToSubscribers(String gameName, GameWebhookEventType eventType, GameWebhookPayload payload) {
        for (WebhookUserEntity subscriber : subscriptionService.getSubscribers(gameName, eventType)) {
            if (subscriber.isActive()) {
                dispatchService.dispatch(subscriber.getCallbackUrl(), payload);
            }
        }
    }

    private static String normalizePhase(String phaseOfGame) {
        if (phaseOfGame == null) return null;
        String normalized = phaseOfGame.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("strategy")) return "strategy";
        if (normalized.startsWith("action")) return "action";
        if (normalized.startsWith("status")) return "status";
        if (normalized.startsWith("agenda")) return "agenda";
        return null;
    }
}
