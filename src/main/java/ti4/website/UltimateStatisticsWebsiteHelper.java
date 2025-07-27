package ti4.website;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticOptIn;
import ti4.service.tigl.TiglGameReport;
import ti4.service.tigl.TiglUsernameChangeRequest;

@Slf4j
public class UltimateStatisticsWebsiteHelper {

    private static final String TI4_ULTIMATE_STATISTICS_API_KEY = System.getenv("TI4_ULTIMATE_STATISTICS_API_KEY");
    private static final String PLAYER_SETTINGS_URL = "https://api.ti4ultimate.com/api/Async/player-settings";
    private static final String TIGL_REPORT_GAMES_URL = "https://api.ti4ultimate.com/api/Tigl/report-game";
    private static final String TIGL_CHANGE_USERNAME_URL = "https://api.ti4ultimate.com/api/Tigl/change-username";
    private static final String TIGL_REPORT_GAMES_SUCCESS_MESSAGE = "TIGL game successfully reported.";
    private static final String TIGL_REPORT_GAMES_FAILURE_MESSAGE =
        "Failed to report TIGL game. Please report manually: https://www.ti4ultimate.com/community/tigl/report-game";
    private static final String TIGL_CHANGE_USERNAME_SUCCESS_MESSAGE = "TIGL nickname successfully updated.";
    private static final String TIGL_CHANGE_USERNAME_FAILURE_MESSAGE = "Failed to change TIGL nickname.";
    private static final String PLAYER_SETTINGS_SUCCESS_MESSAGE = "Successfully logged your decision. Feel free to check out stats at <https://www.ti4ultimate.com/community/async/>.";
    private static final String PLAYER_SETTINGS_FAILURE_MESSAGE = "Failed to change TI4 Ultimate settings.";

    public static void sendTiglGameReport(TiglGameReport request, MessageChannel channel) {
        sendJson(request, TIGL_REPORT_GAMES_URL, channel, TIGL_REPORT_GAMES_SUCCESS_MESSAGE, TIGL_REPORT_GAMES_FAILURE_MESSAGE);
    }

    public static void sendTiglUsernameChange(TiglUsernameChangeRequest request, MessageChannel channel) {
        sendJson(request, TIGL_CHANGE_USERNAME_URL, channel, TIGL_CHANGE_USERNAME_SUCCESS_MESSAGE, TIGL_CHANGE_USERNAME_FAILURE_MESSAGE);
    }

    public static void sendStatisticsOptIn(StatisticOptIn request, MessageChannel channel) {
        sendJson(request, PLAYER_SETTINGS_URL, channel, PLAYER_SETTINGS_SUCCESS_MESSAGE, PLAYER_SETTINGS_FAILURE_MESSAGE);
    }

    private static void sendJson(Object request, String url, MessageChannel channel, String successMessage, String failureMessage) {
        try {
            String json = EgressClientManager.getObjectMapper().writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", TI4_ULTIMATE_STATISTICS_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            EgressClientManager.getHttpClient().sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
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
        } catch (IOException e) {
            BotLogger.error("An IOException occurred while sending a request to TI4 Ultimate Stats: " + url, e);
            MessageHelper.sendMessageToChannel(channel, failureMessage);
        }
    }

    private static void logHttpError(String url, String json, Throwable e) {
        BotLogger.error(String.format("An exception occurred during HTTP call to %s: %s", url, json), e);
    }

    private static void handleErrorResponse(HttpResponse<String> response, MessageChannel channel, String failureMessage) {
        String body = response.body();
        BotLogger.error(failureMessage + "\n```" + body + "```");
        MessageHelper.sendMessageToChannel(channel, failureMessage);
    }
}
