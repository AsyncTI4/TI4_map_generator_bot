package ti4.helpers;

import static ti4.helpers.ImageHelper.writeCompressedFormat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
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

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.ResourceHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameStatsDashboardPayload;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.website.WebsiteOverlay;

public class WebHelper {
    private static final Properties webProperties;

    static {
        webProperties = new Properties();
        try (InputStream input = new FileInputStream(Objects.requireNonNull(ResourceHelper.getInstance().getWebFile("web.properties")))) {
            webProperties.load(input);
        } catch (IOException e) {
            BotLogger.log("Could not load web properties.", e);
        }
    }

    public static void putData(String gameId, Game game) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) //Only upload when setting is true
            return;

        ObjectMapper mapper = new ObjectMapper();
        try (HttpClient client = HttpClient.newHttpClient()) {
            Map<String, Object> exportableFieldMap = game.getExportableFieldMap();
            String json = mapper.writeValueAsString(exportableFieldMap);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("https://bbg9uiqewd.execute-api.us-east-1.amazonaws.com/Prod/map/%s", gameId)))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            BotLogger.log("Could not put data to web server", e);
        }
    }

    public static void putOverlays(Game game) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) //Only upload when setting is true
            return;

        ObjectMapper mapper = new ObjectMapper();
        try (S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build()) {
            Map<String, WebsiteOverlay> overlays = game.getWebsiteOverlays();
            String json = mapper.writeValueAsString(overlays);

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(webProperties.getProperty("bucket"))
                .key(String.format("overlays/%s/%s.json", game.getID(), game.getID()))
                .contentType("application/json")
                .cacheControl("no-cache, no-store, must-revalidate")
                .build();

            s3.putObject(request, RequestBody.fromString(json));
        } catch (Exception e) {
            BotLogger.log("Could not put overlay to web server", e);
        }
    }

    public static void putStats() {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) //Only upload when setting is true
            return;

        ObjectMapper mapper = new ObjectMapper();

        List<GameStatsDashboardPayload> payloads = new ArrayList<>();
        List<String> badGames = new ArrayList<>();
        int count = 0;
        for (Game game : GameManager.getInstance().getGameNameToGame().values()) {
            if (game.isHasEnded() && game.hasWinner()) {
                count++;
                try {
                    // Quick & Dirty bypass for failed json creation
                    GameStatsDashboardPayload payload = new GameStatsDashboardPayload(game);
                    mapper.writeValueAsString(payload);
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

        try (S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build()) {
            String json = mapper.writeValueAsString(payloads);
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(webProperties.getProperty("bucket"))
                .key(String.format("statistics/%s.json", "test"))
                .contentType("application/json")
                .cacheControl("no-cache, no-store, must-revalidate")
                .build();

            s3.putObject(request, RequestBody.fromString(json));
        } catch (Exception e) {
            BotLogger.log("Could not put statistics to web server", e);
        }
    }

    public static void putMap(String gameId, BufferedImage img) {
        putMap(gameId, img, false, null);
    }

    public static void putMap(String gameId, BufferedImage img, Boolean frog, Player player) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) //Only upload when setting is true
            return;

        try (S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build()) {
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
                    .key(String.format(mapPath, gameId, dtstamp))
                    .contentType("image/" + format)
                    .build();
                s3.putObject(request, RequestBody.fromBytes(out.toByteArray()));
            }
        } catch (SdkClientException e) {
            BotLogger.log("Could not add image for game `" + gameId + "` to web server. Likely invalid credentials.", e);
        } catch (Exception e) {
            BotLogger.log("Could not add image for game `" + gameId + "` to web server", e);
        }
    }

    public static void putFile(String gameId, File file) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) //Only upload when setting is true
            return;

        try (S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build()) {
            String jsonPathFormat = "json_saves/%s/%s";

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(webProperties.getProperty("bucket"))
                .key(String.format(jsonPathFormat, gameId, file.getName()))
                .contentType("application/json")
                .build();

            s3.putObject(request, RequestBody.fromFile(file));
        } catch (SdkClientException e) {
            BotLogger.log("Could not add json file for game `" + gameId + "` to web server. Likely invalid credentials.", e);
        } catch (Exception e) {
            BotLogger.log("Could not add json file for game `" + gameId + "` to web server", e);
        }
    }
}
