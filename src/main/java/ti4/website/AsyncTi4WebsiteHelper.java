package ti4.website;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.settings.GlobalSettings;
import ti4.spring.api.image.GameImageService;
import ti4.spring.context.SpringContext;
import ti4.spring.websocket.WebSocketNotifier;
import ti4.website.model.WebBorderAnomalies;
import ti4.website.model.WebCardPool;
import ti4.website.model.WebExpeditions;
import ti4.website.model.WebLaw;
import ti4.website.model.WebObjectives;
import ti4.website.model.WebPlayerArea;
import ti4.website.model.WebScoreBreakdown;
import ti4.website.model.WebStatTilePositions;
import ti4.website.model.WebStrategyCard;
import ti4.website.model.WebTilePositions;
import ti4.website.model.WebTileUnitData;
import ti4.website.model.WebsiteOverlay;

@UtilityClass
public class AsyncTi4WebsiteHelper {

    public static boolean uploadsEnabled() {
        return GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.UPLOAD_DATA_TO_WEB_SERVER.toString(), Boolean.class, false);
    }

    public static void putData(String gameName, Game game) {
        if (!uploadsEnabled()) return;
        String bucket = EgressClientManager.getWebProperties().getProperty("website.bucket");
        if (bucket == null || bucket.isEmpty()) {
            BotLogger.error("S3 bucket not configured.");
            return;
        }

        try {
            Map<String, Object> exportableFieldMap = game.getExportableFieldMap();
            String json = EgressClientManager.getJsonMapper().writeValueAsString(exportableFieldMap);

            List<String> urls = getConfiguredUrls("gamestate.api.urls");
            for (String urlTemplate : urls) {
                String url = urlTemplate.replace("{gameName}", gameName);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                EgressClientManager.getHttpClient()
                        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .exceptionally(e -> {
                            BotLogger.error(
                                    new LogOrigin(game),
                                    "An exception occurred while performing an async send of game data to: " + url,
                                    e);
                            return null;
                        });
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Could not put data to web server", e);
        }
    }

    public static void putPlayerData(String gameId, Game game) {
        String bucket = EgressClientManager.getWebProperties().getProperty("website.bucket");
        boolean isDevMode = !uploadsEnabled() || bucket == null || bucket.isEmpty();

        try {
            List<WebPlayerArea> playerDataList = new ArrayList<>();
            for (Player player : game.getRealPlayersNNeutral()) {
                playerDataList.add(WebPlayerArea.fromPlayer(player, game));
            }

            WebTilePositions webTilePositions = WebTilePositions.fromGame(game);
            Map<String, WebTileUnitData> tileUnitData = WebTileUnitData.fromGame(game);
            WebStatTilePositions webStatTilePositions = WebStatTilePositions.fromGame(game);
            WebObjectives webObjectives = WebObjectives.fromGame(game);
            WebCardPool webCardPool = WebCardPool.fromGame(game);
            WebExpeditions webExpeditions = WebExpeditions.fromGame(game);
            WebBorderAnomalies webBorderAnomalies = WebBorderAnomalies.fromGame(game);

            // Create score breakdowns for each player
            Map<String, WebScoreBreakdown> playerScoreBreakdowns = new HashMap<>();
            for (Player player : game.getRealPlayersNNeutral()) {
                playerScoreBreakdowns.put(player.getFaction(), WebScoreBreakdown.fromPlayer(player, game));
            }

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

            // Create map of initiative -> strategy card ID
            Map<Integer, String> strategyCardIdMap = new HashMap<>();
            var strategyCardSet = game.getStrategyCardSet();
            if (strategyCardSet != null) {
                for (var scModel : strategyCardSet.getStrategyCardModels()) {
                    strategyCardIdMap.put(scModel.getInitiative(), scModel.getId());
                }
            }

            Map<String, Object> webData = new HashMap<>();
            webData.put("versionSchema", 6);
            webData.put("objectives", webObjectives);
            webData.put("playerData", playerDataList);
            webData.put("lawsInPlay", lawsInPlay);
            webData.put("cardPool", webCardPool);
            webData.put("strategyCards", strategyCards);
            webData.put("strategyCardIdMap", strategyCardIdMap);
            webData.put("scoreBreakdowns", playerScoreBreakdowns);
            webData.put("tilePositions", webTilePositions.getTilePositions());
            webData.put("tileUnitData", tileUnitData);
            webData.put("statTilePositions", webStatTilePositions.getStatTilePositions());
            webData.put("ringCount", game.getRingCount());
            webData.put("vpsToWin", game.getVp());
            webData.put("gameRound", game.getRound());
            webData.put("gameName", game.getName());
            webData.put("gameCustomName", game.getCustomName());
            webData.put("tableTalkJumpLink", game.getTabletalkJumpLink());
            webData.put("actionsJumpLink", game.getActionsJumpLink());
            webData.put("expeditions", webExpeditions != null ? webExpeditions.getExpeditions() : null);
            webData.put(
                    "borderAnomalies",
                    webBorderAnomalies.getBorderAnomalies() != null
                                    && !webBorderAnomalies.getBorderAnomalies().isEmpty()
                            ? webBorderAnomalies.getBorderAnomalies()
                            : null);
            webData.put("isTwilightsFallMode", game.isTwilightsFallMode());

            String json = EgressClientManager.getJsonMapper().writeValueAsString(webData);

            if (isDevMode) {
                // Dev/local mode - print to console instead of uploading

                // Uncomment if this is what you're into
                // System.out.println("=== DEV MODE: Web Player Data for game " + gameId + " ===");
                // System.out.println(json);
                // System.out.println("=== END Web Player Data ===");
            } else {
                // Production mode - upload to S3
                putObjectInBucket(
                        String.format("webdata/%s/%s.json", gameId, gameId),
                        AsyncRequestBody.fromString(json),
                        "application/json",
                        "no-cache, no-store, must-revalidate");

                // Upload latest image name if available
                try {
                    GameImageService gameImageService = SpringContext.getBean(GameImageService.class);
                    String latestImageName = gameImageService.getLatestMapImageName(game.getName());
                    if (latestImageName != null && !latestImageName.isEmpty()) {
                        Map<String, String> imageData = new HashMap<>();
                        imageData.put("image", latestImageName);
                        String imageJson = EgressClientManager.getJsonMapper().writeValueAsString(imageData);
                        putObjectInBucket(
                                String.format("webdata/%s/latestImage.json", gameId),
                                AsyncRequestBody.fromString(imageJson),
                                "application/json",
                                "no-cache, no-store, must-revalidate");
                    }
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(game), "Could not upload latest image name to web server", e);
                }

                notifyGameRefreshWebsocket(gameId);
            }
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
            String json = EgressClientManager.getJsonMapper().writeValueAsString(overlays);

            putObjectInBucket(
                    String.format("overlays/%s/%s.json", gameId, gameId),
                    AsyncRequestBody.fromString(json),
                    "application/json",
                    "no-cache, no-store, must-revalidate");
        } catch (Exception e) {
            BotLogger.error("Could not put overlay to web server", e);
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
