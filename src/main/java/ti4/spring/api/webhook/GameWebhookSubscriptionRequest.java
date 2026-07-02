package ti4.spring.api.webhook;

import java.util.List;

public record GameWebhookSubscriptionRequest(List<String> eventTypes) {}
