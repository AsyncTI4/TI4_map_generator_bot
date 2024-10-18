package ti4.helpers;

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
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.ResourceHelper;
import ti4.map.Game;
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
        try {
            Map<String, Object> exportableFieldMap = game.getExportableFieldMap();
            String json = mapper.writeValueAsString(exportableFieldMap);

            HttpClient client = HttpClient.newHttpClient();
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
        try {
            Map<String, WebsiteOverlay> overlays = game.getWebsiteOverlays();
            String json = mapper.writeValueAsString(overlays);

            Region region = Region.US_EAST_1;
            S3Client s3 = S3Client.builder()
                .region(region)
                .build();

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

    public static void putMap(String gameId, BufferedImage img) {
        putMap(gameId, img, false, null);
    }

    public static void putMap(String gameId, BufferedImage img, Boolean frog, Player player) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) //Only upload when setting is true
            return;

        try {
            Region region = Region.US_EAST_1;
            S3Client s3 = S3Client.builder()
                .region(region)
                .build();
            String mapPathFormat;
            if (frog != null && frog && player != null) {
                mapPathFormat = "fogmap/" + player.getUserID() + "/%s/%s.png";
            } else {
                mapPathFormat = "map/%s/%s.png";
            }

            LocalDateTime date = LocalDateTime.now();
            String dtstamp = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(webProperties.getProperty("bucket"))
                .key(String.format(mapPathFormat, gameId, dtstamp))
                .contentType("image/png")
                .build();

            ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("png").next();
            imageWriter.setOutput(ImageIO.createImageOutputStream(out));
            ImageWriteParam defaultWriteParam = imageWriter.getDefaultWriteParam();
            if (defaultWriteParam.canWriteCompressed()) {
                defaultWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                defaultWriteParam.setCompressionQuality(0.01f);
            }

            try {
                imageWriter.write(null, new IIOImage(img, null, null), defaultWriteParam);
            } catch (IOException e) {
                BotLogger.log("Could not write image to web server", e);
                throw new RuntimeException(e);
            }

            s3.putObject(request, RequestBody.fromBytes(out.toByteArray()));
        } catch (SdkClientException e) {
            BotLogger.log("Could not add image for game `" + gameId + "` to web server. Likely invalid credentials.", e);
        } catch (Exception e) {
            BotLogger.log("Could not add image for game `" + gameId + "` to web server", e);
        }
    }

    public static void putFile(String gameId, File file) {
        if (!GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false)) //Only upload when setting is true
            return;

        try {
            Region region = Region.US_EAST_1;
            S3Client s3 = S3Client.builder()
                .region(region)
                .build();
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
