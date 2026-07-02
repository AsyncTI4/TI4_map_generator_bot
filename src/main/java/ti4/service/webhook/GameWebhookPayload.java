package ti4.service.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameWebhookPayload(
        String gameName,
        GameWebhookEventType eventType,
        String previousPhaseOfGame,
        String phaseOfGame,
        int round,
        String activePlayerId,
        String activeFaction,
        Instant timestamp) {}
