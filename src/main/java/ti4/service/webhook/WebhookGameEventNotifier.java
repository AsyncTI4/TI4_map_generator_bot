package ti4.service.webhook;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.spring.service.webhook.GameWebhookSubscriptionService;

@Service
public class WebhookGameEventNotifier implements GameEventNotifier {
    private final WebhookDispatchService webhookDispatchService;
    private final GameWebhookSubscriptionService gameWebhookSubscriptionService;

    public WebhookGameEventNotifier(
            WebhookDispatchService webhookDispatchService,
            GameWebhookSubscriptionService gameWebhookSubscriptionService) {
        this.webhookDispatchService = webhookDispatchService;
        this.gameWebhookSubscriptionService = gameWebhookSubscriptionService;
    }

    @Override
    public void notifyActivePlayerChanged(Game game, Player previousActivePlayer, Player activePlayer) {
        if (Objects.equals(
                previousActivePlayer == null ? null : previousActivePlayer.getUserID(),
                activePlayer == null ? null : activePlayer.getUserID())) {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousActivePlayerId", previousActivePlayer == null ? null : previousActivePlayer.getUserID());
        metadata.put("previousActiveFaction", previousActivePlayer == null ? null : previousActivePlayer.getFaction());
        dispatchIfConfigured(game, GameWebhookEventType.ACTIVE_PLAYER_CHANGED, null, metadata);
    }

    @Override
    public void notifyPhaseChanged(Game game, String previousPhaseOfGame, String currentPhaseOfGame) {
        String previous = normalizePhase(previousPhaseOfGame);
        String current = normalizePhase(currentPhaseOfGame);
        if (current == null || Objects.equals(previous, current)) {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousPhaseOfGame", previous);
        metadata.put("currentPhaseOfGame", current);
        dispatchIfConfigured(game, GameWebhookEventType.PHASE_CHANGED, current, metadata);
    }

    @Override
    public void notifyAgendaVotingStarted(Game game) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(game.getCurrentAgendaInfo())) {
            metadata.put("agendaInfo", game.getCurrentAgendaInfo());
        }
        dispatchIfConfigured(game, GameWebhookEventType.AGENDA_VOTING_STARTED, null, metadata);
    }

    @Override
    public void notifyAgendaResolved(Game game, String winner) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(winner)) {
            metadata.put("winner", winner);
        }
        if (StringUtils.isNotBlank(game.getCurrentAgendaInfo())) {
            metadata.put("agendaInfo", game.getCurrentAgendaInfo());
        }
        dispatchIfConfigured(game, GameWebhookEventType.AGENDA_RESOLVED, null, metadata);
    }

    @Override
    public void notifyPlayerPassed(Game game, Player passedPlayer, boolean autoPass) {
        if (passedPlayer == null) {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("passedPlayerId", passedPlayer.getUserID());
        metadata.put("passedFaction", passedPlayer.getFaction());
        metadata.put("autoPass", autoPass);
        dispatchIfConfigured(game, GameWebhookEventType.PLAYER_PASSED, null, metadata);
    }

    @Override
    public void notifyGameEnded(Game game) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("endedDate", game.getEndedDate());
        dispatchIfConfigured(game, GameWebhookEventType.GAME_ENDED, null, metadata);
    }

    private void dispatchIfConfigured(
            Game game, GameWebhookEventType eventType, String phaseOverride, Map<String, Object> metadata) {
        try {
            Player activePlayer = game.getActivePlayer();
            String phaseOfGame = phaseOverride == null ? normalizePhase(game.getPhaseOfGame()) : phaseOverride;
            if (phaseOfGame == null) {
                phaseOfGame = StringUtils.trimToEmpty(game.getPhaseOfGame());
            }

            GameWebhookEventPayload payload = new GameWebhookEventPayload(
                    game.getName(),
                    eventType.value(),
                    phaseOfGame,
                    game.getRound(),
                    game.getActivePlayerID(),
                    activePlayer == null ? null : activePlayer.getFaction(),
                    Instant.now().toString(),
                    metadata.isEmpty() ? null : metadata);
            dispatchLegacyWebhook(game, payload);
            dispatchWebhookSubscriptions(game, eventType, payload);
        } catch (RuntimeException e) {
            BotLogger.error(new LogOrigin(game), "Failed to notify webhook for event " + eventType.value(), e);
        }
    }

    private void dispatchLegacyWebhook(Game game, GameWebhookEventPayload payload) {
        if (!GameWebhookConfig.isWebhookEnabled(game)) {
            return;
        }
        if (game.isFowMode() && !GameWebhookConfig.isFowAllowed(game)) {
            return;
        }
        String webhookUrl = GameWebhookConfig.getWebhookUrl(game).orElse(null);
        if (StringUtils.isBlank(webhookUrl)) {
            return;
        }
        webhookDispatchService.dispatch(game, webhookUrl, payload);
    }

    private void dispatchWebhookSubscriptions(
            Game game, GameWebhookEventType eventType, GameWebhookEventPayload payload) {
        for (String callbackUrl : gameWebhookSubscriptionService.getCallbackUrls(game.getName(), eventType)) {
            webhookDispatchService.dispatch(game, callbackUrl, payload);
        }
    }

    private String normalizePhase(String phaseOfGame) {
        if (StringUtils.isBlank(phaseOfGame)) {
            return null;
        }
        String phase = phaseOfGame.trim().toLowerCase(Locale.ROOT);
        if (phase.contains("agenda")) {
            return "agenda";
        }
        if (phase.startsWith("status")) {
            return "status";
        }
        if (phase.contains("strategy")) {
            return "strategy";
        }
        if (phase.contains("action")) {
            return "action";
        }
        return null;
    }
}
