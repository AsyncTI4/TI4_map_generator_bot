package ti4.spring.api.webhook;

import java.util.List;
import ti4.spring.service.webhook.PublicGameWebhookEventType;

public record GameWebhookSubscriptionRequest(List<PublicGameWebhookEventType> eventTypes) {}
