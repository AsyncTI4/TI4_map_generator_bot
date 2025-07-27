package ti4.website;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
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
import java.util.Objects;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.ResourceHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticOptIn;
import ti4.service.tigl.TiglGameReport;
import ti4.service.tigl.TiglUsernameChangeRequest;
import ti4.settings.GlobalSettings;
import ti4.website.model.stats.GameStatsDashboardPayload;

public class WebHelper {

    private static final String TI4_ULTIMATE_STATISTICS_API_KEY = System.getenv("TI4_ULTIMATE_STATISTICS_API_KEY");
    private static final int STAT_BATCH_SIZE = 200;
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final S3AsyncClient s3AsyncClient = S3AsyncClient.builder().region(Region.US_EAST_1).build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TIGL_REPORT_FAILURE_MESSAGE =
        "Failed to report TIGL game. Please report manually: https://www.ti4ultimate.com/community/tigl/report-game";
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

    private static List<String> getConfiguredUrls(String propertyKey) {
        String urlsProperty = webProperties.getProperty(propertyKey, "");
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
        
        String primaryBucket = webProperties.getProperty("bucket");
        if (primaryBucket != null && !primaryBucket.isEmpty()) {
            buckets.add(primaryBucket);
        }
        
        String websiteBucket = webProperties.getProperty("website.bucket");
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
            
            s3AsyncClient.putObject(request, body)
                .exceptionally(e -> {
                    BotLogger.error(String.format("An exception occurred while performing an async send to bucket %s", bucket), e);
                    return null;
                });
        }
    }

    public static void putData(String gameName, Game game) {
        if (!sendingToWeb()) return;

        try {
            Map<String, Object> exportableFieldMap = game.getExportableFieldMap();
            String json = objectMapper.writeValueAsString(exportableFieldMap);

            List<String> urls = getConfiguredUrls("gamestate.api.urls");
            for (String urlTemplate : urls) {
                String url = urlTemplate.replace("{gameName}", gameName);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
//        if (!sendingToWeb())  return;

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

            String json = objectMapper.writeValueAsString(webData);

            System.out.println(json);
            
//            putObjectToAllBuckets(
//                String.format("webdata/%s/%s.json", gameId, gameId),
//                AsyncRequestBody.fromString(json),
//                "application/json",
//                "no-cache, no-store, must-revalidate"
//            );
        } catch (IOException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Could not put data to web server", e);
        }
    }

    public static void putOverlays(String gameId, List<WebsiteOverlay> overlays) {
        if (!sendingToWeb()) return;

        try {
            String json = objectMapper.writeValueAsString(overlays);

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

        Path tempFile = Files.createTempFile("statistics", ".json");
        try (OutputStream outputStream = Files.newOutputStream(tempFile);
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

            s3AsyncClient.putObject(req, AsyncRequestBody.fromFile(tempFile))
                .whenComplete((result, ex) -> {
                    synchronized (completedUploads) {
                        completedUploads[0]++;
                        
                        if (ex != null) {
                            BotLogger.error(String.format("Failed to upload game stats to S3 bucket %s.", bucketName), ex);
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

    public static void sendTiglGameReport(TiglGameReport report, MessageChannel channel) {
        try {
            String url = webProperties.getProperty("tigl.report-game.api.url");
            if (url == null) {
                BotLogger.error("TIGL game report URL not set. Property: tigl.report-game.api.url");
            }
            String json = objectMapper.writeValueAsString(report);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", TI4_ULTIMATE_STATISTICS_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response == null) return;
                    if (response.statusCode() == 200) {
                        MessageHelper.sendMessageToChannel(channel, "TIGL game successfully reported.");
                    } else if (response.statusCode() >= 400) {
                        String body = response.body();
                        try {
                            JsonNode node = objectMapper.readTree(body);
                            String title = node.path("problemDetails").path("title").asText();
                            String detail = node.path("problemDetails").path("detail").asText();
                            if (title.isEmpty()) {
                                title = node.path("data").path("errorTitle").asText();
                                detail = node.path("data").path("errorMessage").asText();
                            }
                            BotLogger.error(String.format("Failed to report TIGL game: %s - %s", title, detail));
                            MessageHelper.sendMessageToChannel(channel,
                                String.format("Failed to report TIGL game: %s - %s. Please report manually: https://www.ti4ultimate.com/community/tigl/report-game", title, detail));
                        } catch (Exception ex) {
                            BotLogger.error("Failed to parse TIGL response: " + body, ex);
                            MessageHelper.sendMessageToChannel(channel,
                                TIGL_REPORT_FAILURE_MESSAGE);
                        }
                    }
                })
                .exceptionally(e -> {
                    BotLogger.error(String.format("An exception occurred while sending a TIGL game report to %s: %s", url, json), e);
                    MessageHelper.sendMessageToChannel(channel,
                        TIGL_REPORT_FAILURE_MESSAGE);
                    return null;
                });
        } catch (IOException e) {
            BotLogger.error("An IOException occurred while sending a TIGL game report.", e);
            MessageHelper.sendMessageToChannel(channel,
                TIGL_REPORT_FAILURE_MESSAGE);
        }
    }

    public static void sendTiglUsernameChange(TiglUsernameChangeRequest request, MessageChannel channel) {
        try {
            String url = webProperties.getProperty("tigl.change-username.api.url");
            if (url == null) {
                BotLogger.error("TIGL change username URL not set. Property: tigl.change-username.api.url");
            }
            String json = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", TI4_ULTIMATE_STATISTICS_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response == null) return;
                    if (response.statusCode() == 200) {
                        MessageHelper.sendMessageToChannel(channel, "TIGL nickname successfully updated.");
                    } else if (response.statusCode() >= 400) {
                        String body = response.body();
                        try {
                            JsonNode node = objectMapper.readTree(body);
                            String title = node.path("problemDetails").path("title").asText();
                            String detail = node.path("problemDetails").path("detail").asText();
                            if (title.isEmpty()) {
                                title = node.path("data").path("errorTitle").asText();
                                detail = node.path("data").path("errorMessage").asText();
                            }
                            BotLogger.error(String.format("Failed to change TIGL nickname: %s - %s", title, detail));
                            MessageHelper.sendMessageToChannel(channel,
                                String.format("Failed to change TIGL nickname: %s - %s", title, detail));
                        } catch (Exception ex) {
                            BotLogger.error("Failed to parse TIGL response: " + body, ex);
                            MessageHelper.sendMessageToChannel(channel, "Failed to change TIGL nickname.");
                        }
                    }
                })
                .exceptionally(e -> {
                    BotLogger.error(String.format("An exception occurred while sending a TIGL username change to %s: %s", url, json), e);
                    MessageHelper.sendMessageToChannel(channel, "Failed to change TIGL nickname.");
                    return null;
                });
        } catch (IOException e) {
            BotLogger.error("An IOException occurred while sending a TIGL username change.", e);
            MessageHelper.sendMessageToChannel(channel, "Failed to change TIGL nickname.");
        }
    }

    public static void sendStatisticsOptIn(StatisticOptIn statisticsOptIn) {
        try {
            String statisticsOptInRequest = objectMapper.writeValueAsString(statisticsOptIn);

            List<String> urls = getConfiguredUrls("statistics.api.urls");
            for (String url : urls) {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", TI4_ULTIMATE_STATISTICS_API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(statisticsOptInRequest))
                    .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .exceptionally(e -> {
                        BotLogger.error(String.format("An exception occurred while sending a stats opt in to %s: %s", url, statisticsOptInRequest), e);
                        return null;
                    });
            }
        } catch (IOException e) {
            BotLogger.error("An IOException occurred while sending a stats opt in.", e);
        }
    }
}