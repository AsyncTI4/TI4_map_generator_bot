package ti4.helpers;

import java.awt.image.BufferedImage;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.ResourceHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameStatsDashboardPayload;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.website.WebsiteOverlay;

import static ti4.helpers.ImageHelper.writeCompressedFormat;

public class WebHelper {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final S3AsyncClient s3AsyncClient = S3AsyncClient.builder().region(Region.US_EAST_1).build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Properties webProperties;
    static {
        webProperties = new Properties();
        try (InputStream input = new FileInputStream(Objects.requireNonNull(ResourceHelper.getInstance().getWebFile("web.properties")))) {
            webProperties.load(input);
        } catch (IOException e) {
            BotLogger.log("Could not load web properties.", e);
        }
    }

    public static void putData(String gameName, Game game) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false))
            return;

        try {
            Map<String, Object> exportableFieldMap = game.getExportableFieldMap();
            String json = objectMapper.writeValueAsString(exportableFieldMap);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("https://bbg9uiqewd.execute-api.us-east-1.amazonaws.com/Prod/map/%s", gameName)))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .exceptionally(e -> {
                    BotLogger.log("An exception occurred while performing an async send of game data to the website.", e);
                    return null;
                });
        } catch (IOException e) {
            BotLogger.log("Could not put data to web server", e);
        }
    }

    public static void putOverlays(String gameId, List<WebsiteOverlay> overlays) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false))
            return;

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
                    BotLogger.log("An exception occurred while performing an async send of overlay data to the website.", e);
                    return null;
                });
        } catch (Exception e) {
            BotLogger.log("Could not put overlay to web server", e);
        }
    }

    public static void putStats() {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false))
            return;

        List<GameStatsDashboardPayload> payloads = new ArrayList<>();
        List<String> badGames = new ArrayList<>();
        int count = 0;
        for (Game game : GameManager.getGameNameToGame().values()) {
            if (game.isHasEnded() && game.hasWinner()) {
                count++;
                try {
                    // Quick & Dirty bypass for failed json creation
                    GameStatsDashboardPayload payload = new GameStatsDashboardPayload(game);
                    objectMapper.writeValueAsString(payload);
                    payloads.add(new GameStatsDashboardPayload(game));
                } catch (Exception e) {
                    badGames.add(game.getID());
                    BotLogger.log("Failed to create GameStatsDashboardPayload for game: `" + game.getID() + "`", e);
                }
            }
        }

        String message = "# Statistics Upload\nOut of " + count + " eligible games, the statistics of " + payloads.size() + " games are being uploaded to the web server.";
        if (count != payloads.size()) message += "\nBad Games:\n- " + StringUtils.join(badGames, "\n- ");
        BotLogger.log(message);

        try {
            String json = objectMapper.writeValueAsString(payloads);
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(webProperties.getProperty("bucket"))
                .key(String.format("statistics/%s.json", "test")) // TODO: when this export is final/good, change from "test", tell ParsleySage (stats dashboard dev)
                .contentType("application/json")
                .cacheControl("no-cache, no-store, must-revalidate")
                .build();

            s3AsyncClient.putObject(request, AsyncRequestBody.fromString(json))
                .exceptionally(e -> {
                    BotLogger.log("An exception occurred while performing an async send of game stats to the website.", e);
                    return null;
                });
        } catch (Exception e) {
            BotLogger.log("Could not put statistics to web server", e);
        }
    }

    public static void putMap(String gameName, BufferedImage img) {
        putMap(gameName, img, false, null);
    }

    public static void putMap(String gameName, BufferedImage img, Boolean frog, Player player) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false))
            return;

        try {
            String mapPath;
            if (frog != null && frog && player != null) {
                mapPath = "fogmap/" + player.getUserID() + "/%s/%s.";
            } else {
                mapPath = "map/%s/%s.";
            }

            LocalDateTime date = LocalDateTime.now();
            String dtstamp = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                // TODO: Use webp one day, ImageHelper.writeWebpOrDefaultTo
                String format = "png";
                writeCompressedFormat(img, out, format, 0.1f);
                mapPath += format;
                PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(webProperties.getProperty("bucket"))
                    .key(String.format(mapPath, gameName, dtstamp))
                    .contentType("image/" + format)
                    .build();
                s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(out.toByteArray()))
                    .exceptionally(e -> {
                        BotLogger.log("An exception occurred while performing an async send of the game image to the website.", e);
                        return null;
                    });
            }
        } catch (SdkClientException e) {
            BotLogger.log("Could not add image for game `" + gameName + "` to web server. Likely invalid credentials.", e);
        } catch (Exception e) {
            BotLogger.log("Could not add image for game `" + gameName + "` to web server", e);
        }
    }
}
