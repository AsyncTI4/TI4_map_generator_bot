package ti4.spring.api.webhook;

import java.util.Map;
import ti4.service.webhook.GameWebhookEventType;

public record GameWebhookEventTypeResponse(
        String name,
        String description,
        String trigger,
        String method,
        String contentType,
        int timeoutSeconds,
        String retries,
        Map<String, String> payloadFields) {

    public static GameWebhookEventTypeResponse from(GameWebhookEventType eventType) {
        return new GameWebhookEventTypeResponse(
                eventType.name(),
                eventType.getDescription(),
                eventType.getTrigger(),
                "POST",
                "application/json",
                5,
                "Transient failures only: timeout, IO error, HTTP 408, 429, or 5xx",
                eventType.getPayloadFields());
    }
}
