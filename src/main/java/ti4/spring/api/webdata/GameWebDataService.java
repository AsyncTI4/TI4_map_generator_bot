package ti4.spring.api.webdata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import ti4.cache.CacheManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.FoWHelper;
import ti4.json.JsonMapperManager;
import ti4.service.fow.FOWPlusService;
import ti4.service.option.FOWOptionService.FOWOption;
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

    public String getIfCached(String gameName) {
        return webDataCache.getIfPresent(gameName);
    }

    public void put(String gameName, String serializedWebData) {
        webDataCache.put(gameName, serializedWebData);
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

    /**
     * Builds the fogged view of {@code game} as seen by {@code viewer}: tiles outside
     * {@link FoWHelper#fowFilter} are replaced with the viewer's remembered "ghost" tile (or
     * omitted if never seen); other players' stats/scores are redacted per
     * {@link FoWHelper#canSeeStatsOfPlayer}; names are redacted if the game's HIDE_PLAYER_NAMES
     * option is set; the explore/relic card pool is omitted if HIDE_EXPLORES (or FoW+) is active.
     */
    public String computeFiltered(Game game, Player viewer) {
        try {
            return JsonMapperManager.basic().writeValueAsString(buildFilteredWebData(game, viewer));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize filtered web data for game " + game.getName(), e);
        }
    }

    public static Map<String, Object> buildFilteredWebData(Game game, Player viewer) {
        Set<String> visible = FoWHelper.fowFilter(game, viewer);
        Map<String, Object> webData = buildWebData(game, buildFogSubstitutedTileMap(game, viewer, visible));
        webData.put("viewingAsPlayerId", viewer.getUserID());
        applyStatRedaction(webData, game, viewer);
        applyPlayerNameRedaction(webData, game);
        applyExploreDeckRedaction(webData, game);
        applyControlIdentityRedaction(webData, game, viewer);
        applyObjectiveProgressRedaction(webData, viewer);
        applyLawRedaction(webData, game, viewer);
        applyGhostTileMetadata(webData, viewer, visible);
        return webData;
    }

    private static void applyLawRedaction(Map<String, Object> webData, Game game, Player viewer) {
        @SuppressWarnings("unchecked")
        List<WebLaw> lawsInPlay = (List<WebLaw>) webData.get("lawsInPlay");
        WebLaw.redactElectedFaction(lawsInPlay, game, viewer);
    }

    /**
     * Flags positions outside the viewer's current vision as remembered "ghost" tiles, mirroring the
     * greyed-out fog tile with a "last seen" label the Discord PNG already draws for these same
     * positions (see WebTileUnitData#markGhostTiles).
     */
    private static void applyGhostTileMetadata(Map<String, Object> webData, Player viewer, Set<String> visible) {
        @SuppressWarnings("unchecked")
        Map<String, WebTileUnitData> tileUnitData = (Map<String, WebTileUnitData>) webData.get("tileUnitData");
        WebTileUnitData.markGhostTiles(tileUnitData, visible, viewer);
    }

    private static void applyObjectiveProgressRedaction(Map<String, Object> webData, Player viewer) {
        WebObjectives objectives = (WebObjectives) webData.get("objectives");
        WebObjectives.redactFactionProgress(objectives, viewer);
    }

    private static void applyControlIdentityRedaction(Map<String, Object> webData, Game game, Player viewer) {
        @SuppressWarnings("unchecked")
        Map<String, WebTileUnitData> tileUnitData = (Map<String, WebTileUnitData>) webData.get("tileUnitData");
        WebTileUnitData.redactControlIdentities(tileUnitData, game, viewer);
        WebTileUnitData.redactUnitIdentities(tileUnitData, game, viewer);
    }

    /**
     * Mirrors MapGenerator's private-FoW rendering: a player area is only included at all if the
     * viewer can see that player's stats (FoWHelper#canSeeStatsOfPlayer). Score breakdowns are kept
     * for everyone but trimmed to SCORED entries only for hidden players - scored objectives/relics
     * stay visible regardless of fog, unlike hand/resource stats.
     */
    private static void applyStatRedaction(Map<String, Object> webData, Game game, Player viewer) {
        List<WebPlayerArea> playerDataList = new ArrayList<>();
        Map<String, WebScoreBreakdown> scoreBreakdowns = new HashMap<>();
        for (Player player : game.getRealPlayersNNeutral()) {
            boolean canSeeStats = FoWHelper.canSeeStatsOfPlayer(game, player, viewer);
            if (canSeeStats) {
                playerDataList.add(WebPlayerArea.fromPlayer(player, game));
            }
            scoreBreakdowns.put(
                    player.getFaction(),
                    canSeeStats
                            ? WebScoreBreakdown.fromPlayer(player, game)
                            : WebScoreBreakdown.redacted(player, game));
        }
        webData.put("playerData", playerDataList);
        webData.put("scoreBreakdowns", scoreBreakdowns);
    }

    private static void applyPlayerNameRedaction(Map<String, Object> webData, Game game) {
        if (!game.hideUserNames()) return;
        @SuppressWarnings("unchecked")
        List<WebPlayerArea> playerDataList = (List<WebPlayerArea>) webData.get("playerData");
        for (WebPlayerArea area : playerDataList) {
            area.setUserName(null);
            area.setDisplayName(null);
            area.setDiscordId(null);
        }
    }

    private static void applyExploreDeckRedaction(Map<String, Object> webData, Game game) {
        boolean hideExplores = FOWPlusService.isActive(game) || game.getFowOption(FOWOption.HIDE_EXPLORES);
        if (hideExplores) {
            webData.put("cardPool", null);
        }
    }

    private static Map<String, Tile> buildFogSubstitutedTileMap(Game game, Player viewer, Set<String> visible) {
        Map<String, Tile> substituted = new LinkedHashMap<>();
        for (Map.Entry<String, Tile> entry : game.getTileMap().entrySet()) {
            String position = entry.getKey();
            if (visible.contains(position)) {
                substituted.put(position, entry.getValue());
                continue;
            }
            Tile fogTile = viewer.buildFogTile(position, viewer);
            if (fogTile != null) {
                substituted.put(position, fogTile);
            }
        }
        return substituted;
    }

    private static String serialize(Game game) {
        try {
            return JsonMapperManager.basic().writeValueAsString(buildWebData(game));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize web data for game " + game.getName(), e);
        }
    }

    public static Map<String, Object> buildWebData(Game game) {
        return buildWebData(game, game.getTileMap());
    }

    public static Map<String, Object> buildWebData(Game game, Map<String, Tile> tileMap) {
        List<WebPlayerArea> playerDataList = new ArrayList<>();
        for (Player player : game.getRealPlayersNNeutral()) {
            playerDataList.add(WebPlayerArea.fromPlayer(player, game));
        }

        WebTilePositions webTilePositions = WebTilePositions.fromTileMap(tileMap);
        Map<String, WebTileUnitData> tileUnitData = WebTileUnitData.fromTileMap(game, tileMap);
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
        webData.put("eventSequence", game.getEventSequenceCounter());
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
        webData.put("isFowMode", game.isFowMode());
        webData.put("hidePlayerInfos", game.isFowMode() && game.getFowOption(FOWOption.HIDE_PLAYER_INFOS));
        return webData;
    }
}
