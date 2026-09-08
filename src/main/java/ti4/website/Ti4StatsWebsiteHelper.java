package ti4.website;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import ti4.game.Game;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.website.model.stats.GameStatsDashboardPayload;

@UtilityClass
public class Ti4StatsWebsiteHelper {

    private static final String TI4_STATS_API_KEY = System.getenv("TI4_STATS_API_KEY");
    private static final String TI4_STATS_GAME_SNAPSHOT_IMPORT_URL =
            "https://ti4stats.com/api/v1/asyncti4/game_snapshots_import_jobs";
    private static final String DINGDONG22_DISCORD_NOTIFICATION = "<@1514621912175738924>";

    public static void sendGameStats(Game game) {
        try {
            String payload = JsonMapperManager.basic()
                    .writeValueAsString(Map.of("data", List.of(new GameStatsDashboardPayload(game))));

            HttpRequest httpRequest = buildPostRequest(TI4_STATS_GAME_SNAPSHOT_IMPORT_URL, payload);

            EgressClientManager.getHttpClient()
                    .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response == null) return;
                        int statusCode = response.statusCode();
                        if (statusCode != HttpStatus.ACCEPTED.value()) {
                            BotLogger.error(String.format(
                                    DINGDONG22_DISCORD_NOTIFICATION
                                            + " Unexpected status from sending game stats '%s' to TI4 Stats: '%d' - %s",
                                    game.getID(),
                                    statusCode,
                                    response.body()));
                        }
                    })
                    .exceptionally(e -> {
                        BotLogger.error(
                                String.format(
                                        DINGDONG22_DISCORD_NOTIFICATION
                                                + " An exception occured while sending game stats for game '%s' to TI4 Stats",
                                        game.getID()),
                                e);
                        return null;
                    });
        } catch (Exception e) {
            BotLogger.error(
                    String.format(
                            DINGDONG22_DISCORD_NOTIFICATION
                                    + " An exception occured while sending game stats for game '%s' to TI4 Stats",
                            game.getID()),
                    e);
        }
    }

    private static HttpRequest buildPostRequest(String url, String payload) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TI4_STATS_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
    }
}
