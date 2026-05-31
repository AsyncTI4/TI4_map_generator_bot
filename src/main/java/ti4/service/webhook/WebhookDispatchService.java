package ti4.service.webhook;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Service;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.website.EgressClientManager;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WebhookDispatchService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MILLIS = 500L;

    private final JsonMapper jsonMapper = JsonMapperManager.basic();

    public void dispatch(Game game, String webhookUrl, GameWebhookEventPayload payload) {
        ExecutorServiceManager.runAsync(
                "webhook_dispatch_" + game.getName(), () -> sendWithRetry(game, webhookUrl, payload));
    }

    private void sendWithRetry(Game game, String webhookUrl, GameWebhookEventPayload payload) {
        if (!isWebhookUrlValid(webhookUrl)) {
            BotLogger.warning(new LogOrigin(game), "Skipping webhook dispatch due to invalid URL.");
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response =
                        EgressClientManager.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }

                BotLogger.warning(
                        new LogOrigin(game),
                        "Webhook dispatch returned status "
                                + response.statusCode()
                                + " on attempt "
                                + attempt
                                + ". Response: "
                                + response.body());
            } catch (Exception e) {
                BotLogger.error(new LogOrigin(game), "Webhook dispatch failed on attempt " + attempt, e);
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private boolean isWebhookUrlValid(String webhookUrl) {
        try {
            URI uri = URI.create(webhookUrl);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
