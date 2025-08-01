package ti4.website;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SequenceWriter;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.BotLogger;
import ti4.settings.GlobalSettings;
import ti4.website.model.WebCardPool;
import ti4.website.model.WebLaw;
import ti4.website.model.WebObjectives;
import ti4.website.model.WebPlayerArea;
import ti4.website.model.WebStatTilePositions;
import ti4.website.model.WebStrategyCard;
import ti4.website.model.WebTilePositions;
import ti4.website.model.WebTileUnitData;
import ti4.website.model.WebsiteOverlay;
import ti4.website.model.stats.GameStatsDashboardPayload;

public class AsyncTi4WebsiteHelper {

    private static final int STAT_BATCH_SIZE = 200;

    public static boolean uploadsEnabled() {
        return GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false);
    }

    public static void putData(String gameName, Game game) {
        if (!uploadsEnabled()) return;

        try {
            Map<String, Object> exportableFieldMap = game.getExportableFieldMap();
            String json = EgressClientManager.getObjectMapper().writeValueAsString(exportableFieldMap);

            List<String> urls = getConfiguredUrls("gamestate.api.urls");
            for (String urlTemplate : urls) {
                String url = urlTemplate.replace("{gameName}", gameName);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                EgressClientManager.getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .exceptionally(e -> {
                        BotLogger.error(new BotLogger.LogMessageOrigin(game), "An exception occurred while performing an async send of game data to: " + url, e);
                        return null;
                    });
            }
        } catch (IOException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Could not put data to web server", e);
        }
    }

    public static void putPlayerData(String gameId, Game game) {
        if (!uploadsEnabled())  return;

        try {
            List<WebPlayerArea> playerDataList = new ArrayList<>();
            for (Player player : game.getPlayers().values()) {
                if(!player.isDummy()) {
                    playerDataList.add(WebPlayerArea.fromPlayer(player, game));
                }
            }

            WebTilePositions webTilePositions = WebTilePositions.fromGame(game);
            Map<String, WebTileUnitData> tileUnitData = WebTileUnitData.fromGame(game);
            WebStatTilePositions webStatTilePositions = WebStatTilePositions.fromGame(game);
            WebObjectives webObjectives = WebObjectives.fromGame(game);
            WebCardPool webCardPool = WebCardPool.fromGame(game);

            // Create laws with metadata
            List<WebLaw> lawsInPlay = new ArrayList<>();
            for (Map.Entry<String, Integer> lawEntry : game.getLaws().entrySet()) {
                WebLaw webLaw = WebLaw.fromGameLaw(lawEntry.getKey(), lawEntry.getValue(), game);
                lawsInPlay.add(webLaw);
            }

            // Create strategy cards with trade goods and pick status
            List<WebStrategyCard> strategyCards = new ArrayList<>();
            for (Integer scNumber : game.getScTradeGoods().keySet()) {
                if (scNumber == 0) continue; // Skip the special "0" SC (Naalu's zero token)
                WebStrategyCard webSC = WebStrategyCard.fromGameStrategyCard(scNumber, game);
                strategyCards.add(webSC);
            }

            Map<String, Object> webData = new HashMap<>();
            webData.put("versionSchema", 5);
            webData.put("objectives", webObjectives);
            webData.put("playerData", playerDataList);
            webData.put("lawsInPlay", lawsInPlay);
            webData.put("cardPool", webCardPool);
            webData.put("strategyCards", strategyCards);
            webData.put("tilePositions", webTilePositions.getTilePositions());
            webData.put("tileUnitData", tileUnitData);
            webData.put("statTilePositions", webStatTilePositions.getStatTilePositions());
            webData.put("ringCount", game.getRingCount());
            webData.put("vpsToWin", game.getVp());
            webData.put("gameRound", game.getRound());
            webData.put("gameName", game.getName());
            webData.put("gameCustomName", game.getCustomName());

            String json = EgressClientManager.getObjectMapper().writeValueAsString(webData);

            putObjectToAllBuckets(
                String.format("webdata/%s/%s.json", gameId, gameId),
                AsyncRequestBody.fromString(json),
                "application/json",
                "no-cache, no-store, must-revalidate"
            );
        } catch (IOException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Could not put data to web server", e);
        }
    }

    public static void putOverlays(String gameId, List<WebsiteOverlay> overlays) {
        if (!uploadsEnabled()) return;

        try {
            String json = EgressClientManager.getObjectMapper().writeValueAsString(overlays);

            putObjectToAllBuckets(
                String.format("overlays/%s/%s.json", gameId, gameId),
                AsyncRequestBody.fromString(json),
                "application/json",
                "no-cache, no-store, must-revalidate"
            );
        } catch (Exception e) {
            BotLogger.error("Could not put overlay to web server", e);
        }
    }

    // If this becomes a resource hog again, the next step would probably be to switch to MultipartUpload
    public static void putStats() throws IOException {
        if (!uploadsEnabled()) return;

        String bucket = EgressClientManager.getWebProperties().getProperty("bucket");
        if (bucket == null || bucket.isEmpty()) {
            BotLogger.error("S3 bucket not configured.");
            return;
        }

        List<String> badGames = new ArrayList<>();
        int eligible = 0;
        int uploaded = 0;
        int currentBatchSize  = 0;

        Path tempFile = Files.createTempFile("statistics", ".json");
        try (OutputStream outputStream = Files.newOutputStream(tempFile);
                SequenceWriter writer = EgressClientManager.getObjectMapper().writer().writeValuesAsArray(outputStream)) {
            for (ManagedGame managedGame : GameManager.getManagedGames()) {
                if (managedGame.getRound() <= 2 && (!managedGame.isHasEnded() || !managedGame.isHasWinner())) {
                    continue;
                }

                eligible++;

                try {
                    JsonNode node = EgressClientManager.getObjectMapper().valueToTree(new GameStatsDashboardPayload(managedGame.getGame()));
                    writer.write(node);
                    uploaded++;
                    currentBatchSize++;
                    if (currentBatchSize == STAT_BATCH_SIZE) {
                        writer.flush();
                        currentBatchSize = 0;
                    }
                } catch (Exception e) {
                    badGames.add(managedGame.getName());
                    BotLogger.error(
                        String.format("Failed to create GameStatsDashboardPayload for game: `%s`", managedGame.getName()), e);
                }
            }

            writer.flush();
        }

        long fileSize = Files.size(tempFile);
        String msg = String.format("# Uploading statistics to S3 (%.2f MB)... \nOut of %d eligible games, %d games are being uploaded.",
            fileSize / (1024d * 1024d), eligible, uploaded);
        if (eligible != uploaded) {
            msg += "\nBad games (first 10):\n- " + String.join("\n- ", badGames.subList(0, Math.min(10, badGames.size())));
        }
        BotLogger.info(msg);

        List<String> buckets = getConfiguredBuckets();
        int[] completedUploads = {0};
        int totalUploads = buckets.size();
        
        for (String bucketName : buckets) {
            PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucketName)
                .key("statistics/statistics.json")
                .contentType("application/json")
                .cacheControl("no-cache, no-store, must-revalidate")
                .build();

            EgressClientManager.getS3AsyncClient().putObject(req, AsyncRequestBody.fromFile(tempFile))
                .whenComplete((result, throwable) -> {
                    synchronized (completedUploads) {
                        completedUploads[0]++;
                        
                        if (throwable != null) {
                            BotLogger.error(String.format("Failed to upload game stats to S3 bucket %s.", bucketName), throwable);
                        } else {
                            BotLogger.info(String.format("Statistics upload to bucket %s complete.", bucketName));
                        }
                        
                        if (completedUploads[0] == totalUploads) {
                            try {
                                Files.deleteIfExists(tempFile);
                            } catch (IOException e) {
                                BotLogger.error("Failed to delete temporary stats file", e);
                            }
                        }
                    }
                });
        }
    }

    public static void putMap(String gameName, byte[] imageBytes, boolean frog, Player player) {
        if (!uploadsEnabled()) return;

        try {
            String mapPath;
            if (frog && player != null) {
                mapPath = "fogmap/" + player.getUserID() + "/%s/%s.jpg";
            } else {
                mapPath = "map/%s/%s.jpg";
            }

            LocalDateTime date = LocalDateTime.now();
            String dtstamp = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            putObjectToAllBuckets(
                String.format(mapPath, gameName, dtstamp),
                AsyncRequestBody.fromBytes(imageBytes),
                "image/jpg",
                null
            );
        } catch (SdkClientException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(player), "Could not add image for game `" + gameName + "` to web server. Likely invalid credentials.", e);
        }
    }

    private static List<String> getConfiguredUrls(String propertyKey) {
        String urlsProperty = EgressClientManager.getWebProperties().getProperty(propertyKey, "");
        List<String> urls = new ArrayList<>();

        if (!urlsProperty.isEmpty()) {
            String[] urlArray = urlsProperty.split(",");
            for (String url : urlArray) {
                String trimmedUrl = url.trim();
                if (!trimmedUrl.isEmpty()) {
                    urls.add(trimmedUrl);
                }
            }
        }

        return urls;
    }

    private static List<String> getConfiguredBuckets() {
        List<String> buckets = new ArrayList<>();

        String primaryBucket = EgressClientManager.getWebProperties().getProperty("bucket");
        if (primaryBucket != null && !primaryBucket.isEmpty()) {
            buckets.add(primaryBucket);
        }

        String websiteBucket = EgressClientManager.getWebProperties().getProperty("website.bucket");
        if (websiteBucket != null && !websiteBucket.isEmpty()) {
            buckets.add(websiteBucket);
        }

        return buckets;
    }

    private static void putObjectToAllBuckets(String key, AsyncRequestBody body, String contentType, String cacheControl) {
        List<String> buckets = getConfiguredBuckets();

        for (String bucket : buckets) {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);

            if (cacheControl != null) {
                requestBuilder.cacheControl(cacheControl);
            }

            PutObjectRequest request = requestBuilder.build();

            BotLogger.warning(String.format("An request to put player data was processed for key: %s bucket: %s", key, bucket));

            EgressClientManager.getS3AsyncClient().putObject(request, body).exceptionally(e -> {
                    BotLogger.error(String.format("An exception occurred while performing an async send to bucket %s", bucket), e);
                    return null;
                });
        }
    }
}