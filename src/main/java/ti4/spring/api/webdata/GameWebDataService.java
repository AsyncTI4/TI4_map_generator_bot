package ti4.spring.api.webdata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import ti4.cache.CacheManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.json.JsonMapperManager;
import ti4.website.model.WebBorderAnomalies;
import ti4.website.model.WebCardPool;
import ti4.website.model.WebExpeditions;
import ti4.website.model.WebGameState;
import ti4.website.model.WebLaw;
import ti4.website.model.WebObjectives;
import ti4.website.model.WebPlayerArea;
import ti4.website.model.WebScoreBreakdown;
import ti4.website.model.WebStatTilePositions;
import ti4.website.model.WebStrategyCard;
import ti4.website.model.WebTilePositions;
import ti4.website.model.WebTileUnitData;

@Service
public class GameWebDataService {

    private static final Duration WEB_DATA_TTL = Duration.ofMinutes(30);
    private static final String CACHE_NAME = "gameWebDataCache";
    private static final int MAX_GAMES_IN_MEMORY = 500;

    private final Cache<String, String> webDataCache = createCache();

    public String getOrCompute(String gameName) {
        return webDataCache.get(gameName, this::computeForGameName);
    }

    public void put(String gameName, Game game) {
        webDataCache.put(gameName, serialize(game));
    }

    private String computeForGameName(String gameName) {
        var managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null || managedGame.getGame() == null) {
            throw new IllegalArgumentException("Unknown game: " + gameName);
        }
        return serialize(managedGame.getGame());
    }

    private static Cache<String, String> createCache() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(MAX_GAMES_IN_MEMORY)
                .expireAfterWrite(WEB_DATA_TTL)
                .recordStats()
                .build();
        CacheManager.registerCache(CACHE_NAME, cache);
        return cache;
    }

    private static String serialize(Game game) {
        try {
            return JsonMapperManager.basic().writeValueAsString(buildWebData(game));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize web data for game " + game.getName(), e);
        }
    }

    public static Map<String, Object> buildWebData(Game game) {
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

        Map<String, WebScoreBreakdown> playerScoreBreakdowns = new HashMap<>();
        for (Player player : game.getRealPlayersNNeutral()) {
            playerScoreBreakdowns.put(player.getFaction(), WebScoreBreakdown.fromPlayer(player, game));
        }

        List<WebLaw> lawsInPlay = new ArrayList<>();
        for (Map.Entry<String, Integer> lawEntry : game.getLaws().entrySet()) {
            lawsInPlay.add(WebLaw.fromGameLaw(lawEntry.getKey(), lawEntry.getValue(), game));
        }

        List<WebStrategyCard> strategyCards = new ArrayList<>();
        for (Integer scNumber : game.getScTradeGoods().keySet()) {
            if (scNumber == 0) continue;
            strategyCards.add(WebStrategyCard.fromGameStrategyCard(scNumber, game));
        }

        Map<Integer, String> strategyCardIdMap = new HashMap<>();
        var strategyCardSet = game.getStrategyCardSet();
        if (strategyCardSet != null) {
            for (var scModel : strategyCardSet.getStrategyCardModels()) {
                strategyCardIdMap.put(scModel.getInitiative(), scModel.getId());
            }
        }

        Map<String, Object> webData = new LinkedHashMap<>();
        webData.put("versionSchema", 7);
        webData.put("gameState", WebGameState.fromGame(game));
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
        return webData;
    }
}
