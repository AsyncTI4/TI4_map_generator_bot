package ti4.website;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticOptIn;
import ti4.service.tigl.TiglGameReport;
import ti4.service.tigl.TiglRankHistoryRequest;
import ti4.service.tigl.TiglRankHistoryResponse;
import ti4.service.tigl.TiglUsernameChangeRequest;
import tools.jackson.databind.JsonNode;

@Slf4j
@UtilityClass
public class UltimateStatisticsWebsiteHelper {

    private static final String TI4_ULTIMATE_STATISTICS_API_KEY = System.getenv("TI4_ULTIMATE_STATISTICS_API_KEY");
    private static final String PLAYER_SETTINGS_URL = "https://api.ti4ultimate.com/api/Async/player-settings";
    private static final String TIGL_REPORT_GAMES_URL = "https://api.ti4ultimate.com/api/Tigl/report-game";
    private static final String TIGL_RANK_HISTORY_URL = "https://api.ti4ultimate.com/api/Tigl/tigl-player-rank-history";
    private static final String TIGL_CHANGE_USERNAME_URL = "https://api.ti4ultimate.com/api/Tigl/change-username";
    private static final String TIGL_REPORT_GAMES_SUCCESS_MESSAGE = "TIGL game upload successful.";
    private static final String TIGL_REPORT_GAMES_FAILURE_MESSAGE =
            "Failed to report TIGL game. Please ping BLT or Lazik to check how to proceed.";
    private static final String TIGL_CHANGE_USERNAME_SUCCESS_MESSAGE = "TIGL nickname successfully updated.";
    private static final String TIGL_CHANGE_USERNAME_FAILURE_MESSAGE = "Failed to change TIGL nickname.";
    private static final String PLAYER_SETTINGS_SUCCESS_MESSAGE =
            "Successfully logged your decision. Feel free to check out stats at <https://www.ti4ultimate.com/community/async/>.";
    private static final String PLAYER_SETTINGS_FAILURE_MESSAGE = "Failed to change TI4 Ultimate settings.";
    private static final String LAZIK_DISCORD_NOTIFICATION = "<@206450549371961346>";

    public static void sendTiglGameReport(TiglGameReport request, MessageChannel channel) {
        sendJson(
                request,
                TIGL_REPORT_GAMES_URL,
                channel,
                TIGL_REPORT_GAMES_SUCCESS_MESSAGE,
                TIGL_REPORT_GAMES_FAILURE_MESSAGE);
    }

    public static void sendTiglUsernameChange(TiglUsernameChangeRequest request, MessageChannel channel) {
        sendJson(
                request,
                TIGL_CHANGE_USERNAME_URL,
                channel,
                TIGL_CHANGE_USERNAME_SUCCESS_MESSAGE,
                TIGL_CHANGE_USERNAME_FAILURE_MESSAGE);
    }

    public static void sendStatisticsOptIn(StatisticOptIn request, MessageChannel channel) {
        sendJson(
                request,
                PLAYER_SETTINGS_URL,
                channel,
                PLAYER_SETTINGS_SUCCESS_MESSAGE,
                PLAYER_SETTINGS_FAILURE_MESSAGE);
    }

    public static TiglRankHistoryResponse fetchTiglRankHistory(TiglRankHistoryRequest request) {
        return postForJson(request, TIGL_RANK_HISTORY_URL, TiglRankHistoryResponse.class);
    }

    private static <T> T postForJson(Object request, String url, Class<T> responseType) {
        if (StringUtils.isBlank(TI4_ULTIMATE_STATISTICS_API_KEY)) {
            throw new UltimateStatisticsApiException(
                    "The TI4 Ultimate API key is not configured on this bot instance.");
        }
        try {
            String json = JsonMapperManager.basic().writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", TI4_ULTIMATE_STATISTICS_API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    EgressClientManager.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return JsonMapperManager.basic().readValue(response.body(), responseType);
            }
            throw new UltimateStatisticsApiException(describeFailure(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UltimateStatisticsApiException("The request to TI4 Ultimate was interrupted.");
        } catch (UltimateStatisticsApiException e) {
            throw e;
        } catch (Exception e) {
            BotLogger.error("An exception occurred while calling TI4 Ultimate Stats: " + url, e);
            throw new UltimateStatisticsApiException("Could not reach TI4 Ultimate.");
        }
    }

    private static String describeFailure(HttpResponse<String> response) {
        try {
            JsonNode node = JsonMapperManager.basic().readTree(response.body());
            String gatewayMessage = node.path("message").asText();
            if (!gatewayMessage.isEmpty()) {
                return gatewayMessage;
            }
            String details = describeError(node);
            if (!details.isEmpty()) {
                return details;
            }
        } catch (Exception e) {
            BotLogger.error("Failed to parse TI4 Ultimate error response", e);
        }
        return "HTTP " + response.statusCode();
    }

    private static void sendJson(
            Object request, String url, MessageChannel channel, String successMessage, String failureMessage) {
        try {
            String json = JsonMapperManager.basic().writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", TI4_ULTIMATE_STATISTICS_API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            EgressClientManager.getHttpClient()
                    .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response == null) return;
                        if (response.statusCode() == 200) {
                            MessageHelper.sendMessageToChannel(channel, successMessage);
                            return;
                        }
                        if (response.statusCode() >= 400) {
                            handleErrorResponse(response, channel, failureMessage);
                        }
                    })
                    .exceptionally(e -> {
                        logHttpError(url, json, e);
                        MessageHelper.sendMessageToChannel(channel, failureMessage);
                        return null;
                    });
        } catch (Exception e) {
            BotLogger.error(
                    LAZIK_DISCORD_NOTIFICATION + " An exception occurred while sending a request to TI4 "
                            + "Ultimate Stats: " + url,
                    e);
            MessageHelper.sendMessageToChannel(channel, failureMessage);
        }
    }

    private static void logHttpError(String url, String json, Throwable e) {
        BotLogger.error(
                String.format(
                        "%s An exception occurred during HTTP call to %s: %s", LAZIK_DISCORD_NOTIFICATION, url, json),
                e);
    }

    private static void handleErrorResponse(
            HttpResponse<String> response, MessageChannel channel, String failureMessage) {
        String body = response.body();
        JsonNode node = null;
        try {
            node = JsonMapperManager.basic().readTree(body);
        } catch (Exception e) {
            BotLogger.error("Failed to parse TI4 Ultimate error response", e);
        }

        String expected = node == null ? null : describeExpectedRejection(node);
        if (expected != null) {
            BotLogger.info("TI4 Ultimate rejected a request as expected: " + expected + "\n```" + body + "```");
            MessageHelper.sendMessageToChannel(channel, expected);
            return;
        }

        BotLogger.error(LAZIK_DISCORD_NOTIFICATION + " " + failureMessage + "\n```" + body + "```");
        if (node != null) {
            String details = describeError(node);
            if (!details.isEmpty()) {
                MessageHelper.sendMessageToChannel(channel, String.format("%s (%s)", failureMessage, details));
                return;
            }
        }
        MessageHelper.sendMessageToChannel(channel, failureMessage);
    }

    static String describeExpectedRejection(JsonNode node) {
        if (isAlreadyReported(node)) {
            String message = errorMessage(node);
            return message.isEmpty()
                    ? "This game was already reported to TIGL - nothing was sent."
                    : "This game was already reported to TIGL - " + message;
        }
        if (isMissingPlayerProfile(node)) {
            return "You do not have a TI4 Ultimate profile yet. Profiles are created once a game you are in reaches"
                    + " round 2, so please try again after that.";
        }
        return null;
    }

    static boolean isAlreadyReported(JsonNode node) {
        return StringUtils.containsIgnoreCase(errorTitle(node), "already reported");
    }

    static boolean isMissingPlayerProfile(JsonNode node) {
        String message = errorMessage(node);
        return StringUtils.containsIgnoreCase(message, "player profile")
                && StringUtils.containsIgnoreCase(message, "not found");
    }

    private static String describeError(JsonNode node) {
        String title = errorTitle(node);
        String message = errorMessage(node);
        if (message.isEmpty()) {
            return title;
        }
        return title.isEmpty() ? message : title + " - " + message;
    }

    private static String errorTitle(JsonNode node) {
        return node.path("data").path("errorTitle").asText();
    }

    private static String errorMessage(JsonNode node) {
        return node.path("data").path("errorMessage").asText();
    }
}
