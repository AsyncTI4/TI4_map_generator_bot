package ti4.service.webhook;

import java.util.Map;

public record GameWebhookEventPayload(
        String gameName,
        String eventType,
        String phaseOfGame,
        int round,
        String activePlayerId,
        String activeFaction,
        String timestamp,
        Map<String, Object> metadata) {}
