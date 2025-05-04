package ti4.helpers;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.ResourceHelper;
import ti4.map.Game;
import ti4.map.GameStatsDashboardPayload;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.settings.GlobalSettings;
import ti4.website.WebsiteOverlay;

public class WebHelper {

    private static final int STAT_BATCH_SIZE = 200;
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final S3AsyncClient s3AsyncClient = S3AsyncClient.builder().region(Region.US_EAST_1).build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Properties webProperties;
    static {
        webProperties = new Properties();
        try (InputStream input = new FileInputStream(Objects.requireNonNull(ResourceHelper.getInstance().getWebFile("web.properties")))) {
            webProperties.load(input);
        } catch (IOException e) {
            BotLogger.error("Could not load web properties.", e);
        }
    }

    public static boolean sendingToWeb() {
        return GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false);
    }

    public static void putData(String gameName, Game game) {
        if (!sendingToWeb()) return;

        try {
            Map<String, Object> exportableFieldMap = game.getExportableFieldMap();
            String json = objectMapper.writeValueAsString(exportableFieldMap);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("https://bbg9uiqewd.execute-api.us-east-1.amazonaws.com/Prod/map/%s", gameName)))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .exceptionally(e -> {
                    BotLogger.error(new BotLogger.LogMessageOrigin(game), "An exception occurred while performing an async send of game data to the website.", e);
                    return null;
                });
        } catch (IOException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Could not put data to web server", e);
        }
    }

    public static void putOverlays(String gameId, List<WebsiteOverlay> overlays) {
        if (!sendingToWeb()) return;

        try {
            String json = objectMapper.writeValueAsString(overlays);

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(webProperties.getProperty("bucket"))
                .key(String.format("overlays/%s/%s.json", gameId, gameId))
                .contentType("application/json")
                .cacheControl("no-cache, no-store, must-revalidate")
                .build();

            s3AsyncClient.putObject(request, AsyncRequestBody.fromString(json))
                .exceptionally(e -> {
                    BotLogger.error("An exception occurred while performing an async send of overlay data to the website.", e);
                    return null;
                });
        } catch (Exception e) {
            BotLogger.error("Could not put overlay to web server", e);
        }
    }

    public static void putStats() {
        if (!sendingToWeb()) return;

        String bucket = webProperties.getProperty("bucket");
        if (bucket == null || bucket.isEmpty()) {
            BotLogger.error("S3 bucket not configured.");
            return;
        }

        List<String> badGames = new ArrayList<>();
        int eligible = 0;
        int uploaded = 0;
        int currentBatchSize  = 0;

        try (var outputStream = new ByteArrayOutputStream(512 * 1024);
                SequenceWriter writer = objectMapper.writer().writeValuesAsArray(outputStream)) {
            for (ManagedGame managedGame : GameManager.getManagedGames()) {
                if (managedGame.getRound() <= 2 && (!managedGame.isHasEnded() || !managedGame.isHasWinner())) {
                    continue;
                }

                eligible++;

                try {
                    JsonNode node = objectMapper.valueToTree(new GameStatsDashboardPayload(managedGame.getGame()));
                    writer.write(node);
                    uploaded++;
                    currentBatchSize++;
                    if (currentBatchSize == STAT_BATCH_SIZE) {
                        writer.flush();
                        currentBatchSize = 0;
                    }
                } catch (Exception ex) {
                    badGames.add(managedGame.getName());
                    BotLogger.error(
                        String.format("Failed to create GameStatsDashboardPayload for game: `%s`", managedGame.getName()), ex);
                }
            }

            writer.flush();

            String msg = String.format("# Uploading statistics to S3 (%s MB)... \nOut of %s eligible games, %s games are being uploaded.",
                outputStream.size() * 1_000_000L, eligible, uploaded);
            if (eligible != uploaded) {
                msg += "\nBad games (first 10):\n- " + String.join("\n- ", badGames.subList(0, Math.min(10, badGames.size())));
            }
            BotLogger.info(msg);

            PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key("statistics/statistics.json")
                .contentType("application/json")
                .cacheControl("no-cache, no-store, must-revalidate")
                .build();

            s3AsyncClient.putObject(req, AsyncRequestBody.fromBytes(outputStream.toByteArray()))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        BotLogger.error("Failed to upload game stats to S3.", ex);
                    } else {
                        BotLogger.info("Statistics upload complete.");
                    }
                });
        } catch (Exception e) {
            BotLogger.error("Could not put statistics to web server", e);
        }
    }

    public static void putMap(String gameName, byte[] imageBytes) {
        putMap(gameName, imageBytes, false, null);
    }

    public static void putMap(String gameName, byte[] imageBytes, boolean frog, Player player) {
        if (!sendingToWeb()) return;

        try {
            String mapPath;
            if (frog && player != null) {
                mapPath = "fogmap/" + player.getUserID() + "/%s/%s.jpg";
            } else {
                mapPath = "map/%s/%s.jpg";
            }

            LocalDateTime date = LocalDateTime.now();
            String dtstamp = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(webProperties.getProperty("bucket"))
                .key(String.format(mapPath, gameName, dtstamp))
                .contentType("image/jpg")
                .build();
            s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(imageBytes))
                .exceptionally(e -> {
                    BotLogger.error(new BotLogger.LogMessageOrigin(player), "An exception occurred while performing an async send of the game image to the website.", e);
                    return null;
                });
        } catch (SdkClientException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(player), "Could not add image for game `" + gameName + "` to web server. Likely invalid credentials.", e);
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(player), "Could not add image for game `" + gameName + "` to web server", e);
        }
    }
}
