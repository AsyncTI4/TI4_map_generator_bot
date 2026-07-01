package ti4.service.webhook;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Service;
import ti4.executors.ExecutorServiceManager;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.website.EgressClientManager;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WebhookDispatchService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public WebhookDispatchService() {
        this(JsonMapperManager.basic(), EgressClientManager.getHttpClient());
    }

    WebhookDispatchService(JsonMapper jsonMapper, HttpClient httpClient) {
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
    }

    public void dispatch(String callbackUrl, GameWebhookPayload payload) {
        ExecutorServiceManager.runAsync("webhook-dispatch", () -> dispatchWithRetry(callbackUrl, payload));
    }

    private void dispatchWithRetry(String callbackUrl, GameWebhookPayload payload) {
        boolean retry;
        try {
            HttpResponse<String> response = send(callbackUrl, payload);
            retry = shouldRetry(response.statusCode());
            if (retry) logStatus(callbackUrl, response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            BotLogger.warning("Webhook dispatch interrupted for host `" + host(callbackUrl) + "`.");
            return;
        } catch (IOException e) {
            retry = true;
            BotLogger.warning("Webhook dispatch to host `" + host(callbackUrl) + "` failed with transient exception `"
                    + e.getClass().getSimpleName() + "`; retrying once.");
        } catch (Exception e) {
            BotLogger.warning("Webhook dispatch failed for host `" + host(callbackUrl) + "` with exception `"
                    + e.getClass().getSimpleName() + "`.");
            return;
        }

        if (!retry) return;
        try {
            HttpResponse<String> retryResponse = send(callbackUrl, payload);
            logStatus(callbackUrl, retryResponse.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            BotLogger.warning("Webhook dispatch retry interrupted for host `" + host(callbackUrl) + "`.");
        } catch (Exception e) {
            BotLogger.warning("Webhook dispatch retry failed for host `" + host(callbackUrl) + "` with exception `"
                    + e.getClass().getSimpleName() + "`.");
        }
    }

    private HttpResponse<String> send(String callbackUrl, GameWebhookPayload payload) throws Exception {
        String body = jsonMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(callbackUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void logStatus(String callbackUrl, int statusCode) {
        BotLogger.info("Webhook dispatch to host `" + host(callbackUrl) + "` returned HTTP " + statusCode + ".");
    }

    private static String host(String callbackUrl) {
        try {
            return URI.create(callbackUrl).getHost();
        } catch (Exception e) {
            return "unknown";
        }
    }

    static boolean shouldRetry(int statusCode) {
        return statusCode == 408 || statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }
}
