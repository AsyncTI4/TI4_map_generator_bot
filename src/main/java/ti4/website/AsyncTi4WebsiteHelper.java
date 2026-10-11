package ti4.website;

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

@UtilityClass
public class AsyncTi4WebsiteHelper {

    public static boolean uploadsEnabled() {
        return GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.getAsBoolean(false);
    }

    public static void putPlayerData(String gameId, Game game) {
        String bucket = EgressClientManager.getWebsiteBucket();
        // WebSocketNotifier is the sole writer of the web-data cache (writing it here
        // would desync the websocket diff baseline from what clients last received).
        if (uploadsEnabled() && bucket != null && !bucket.isEmpty()) {
            // Upload latest image name if available for legacy consumers.
            uploadLatestImageName(gameId, game, bucket);
        }
        notifyGameRefreshWebsocket(gameId);
    }

    private static void uploadLatestImageName(String gameId, Game game, String bucket) {
        try {
            String latestImageName =
                    SpringContext.getBean(GameImageService.class).getLatestMapImageName(game.getName());
            if (latestImageName == null || latestImageName.isEmpty()) return;
            String imageJson = JsonMapperManager.basic().writeValueAsString(Map.of("image", latestImageName));
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(String.format("webdata/%s/latestImage.json", gameId))
                    .contentType("application/json")
                    .cacheControl("no-cache, no-store, must-revalidate")
                    .build();
            EgressClientManager.getS3AsyncClient()
                    .putObject(request, AsyncRequestBody.fromString(imageJson))
                    .exceptionally(e -> {
                        BotLogger.error(
                                String.format(
                                        "An exception occurred while performing an async send to bucket %s", bucket),
                                e);
                        return null;
                    });
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Could not upload latest image name to web server", e);
        }
    }

    private static void notifyGameRefreshWebsocket(String gameId) {
        try {
            SpringContext.getBean(WebSocketNotifier.class).notifyGameRefresh(gameId);
        } catch (Exception ignored) {
        }
    }
}
