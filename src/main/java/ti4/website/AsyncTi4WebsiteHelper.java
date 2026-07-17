package ti4.website;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.game.Game;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.settings.GlobalSettings;
import ti4.spring.api.image.GameImageService;
import ti4.spring.context.SpringContext;
import ti4.spring.websocket.WebSocketNotifier;
import ti4.website.model.WebsiteOverlay;

@UtilityClass
public class AsyncTi4WebsiteHelper {

    public static boolean uploadsEnabled() {
        return GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, Boolean.FALSE);
    }

    public static void putPlayerData(String gameId, Game game) {
        String bucket = EgressClientManager.getWebProperties().getProperty("website.bucket");
        boolean isDevMode = !uploadsEnabled() || bucket == null || bucket.isEmpty();

        try {
            // WebSocketNotifier is the sole writer of the web-data cache (writing it here
            // would desync the websocket diff baseline from what clients last received).
            if (!isDevMode) {
                // Upload latest image name if available for legacy consumers.
                try {
                    GameImageService gameImageService = SpringContext.getBean(GameImageService.class);
                    String latestImageName = gameImageService.getLatestMapImageName(game.getName());
                    if (latestImageName != null && !latestImageName.isEmpty()) {
                        Map<String, String> imageData = new HashMap<>();
                        imageData.put("image", latestImageName);
                        String imageJson = JsonMapperManager.basic().writeValueAsString(imageData);
                        putObjectInBucket(
                                String.format("webdata/%s/latestImage.json", gameId),
                                AsyncRequestBody.fromString(imageJson),
                                "application/json",
                                "no-cache, no-store, must-revalidate");
                    }
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(game), "Could not upload latest image name to web server", e);
                }
            }

            notifyGameRefreshWebsocket(gameId);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Could not put data to web server", e);
        }
    }

    private static void notifyGameRefreshWebsocket(String gameId) {
        try {
            SpringContext.getBean(WebSocketNotifier.class).notifyGameRefresh(gameId);
        } catch (Exception ignored) {
        }
    }

    public static void putOverlays(String gameId, List<WebsiteOverlay> overlays) {
        if (!uploadsEnabled()) return;
        String bucket = EgressClientManager.getWebProperties().getProperty("website.bucket");
        if (bucket == null || bucket.isEmpty()) {
            BotLogger.error("S3 bucket not configured.");
            return;
        }

        try {
            String json = JsonMapperManager.basic().writeValueAsString(overlays);

            putObjectInBucket(
                    String.format("overlays/%s/%s.json", gameId, gameId),
                    AsyncRequestBody.fromString(json),
                    "application/json",
                    "no-cache, no-store, must-revalidate");
        } catch (Exception e) {
            BotLogger.error("Could not put overlay to web server", e);
        }
    }

    private static void putObjectInBucket(String key, AsyncRequestBody body, String contentType, String cacheControl) {
        String websiteBucket = EgressClientManager.getWebProperties().getProperty("website.bucket");

        PutObjectRequest.Builder requestBuilder =
                PutObjectRequest.builder().bucket(websiteBucket).key(key).contentType(contentType);

        if (cacheControl != null) {
            requestBuilder.cacheControl(cacheControl);
        }

        PutObjectRequest request = requestBuilder.build();

        EgressClientManager.getS3AsyncClient().putObject(request, body).exceptionally(e -> {
            BotLogger.error(
                    String.format("An exception occurred while performing an async send to bucket %s", websiteBucket),
                    e);
            return null;
        });
    }
}
