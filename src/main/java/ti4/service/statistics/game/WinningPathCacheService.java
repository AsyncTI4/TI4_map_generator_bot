package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.experimental.UtilityClass;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.BotLogger;

@UtilityClass
public class WinningPathCacheService {

    private static final Cache<CacheKey, Map<String, Integer>> WINNING_PATH_CACHE =
            Caffeine.newBuilder().build();
    private static boolean hasBeenComputed;

    public static synchronized void recomputeCache() {
        BotLogger.info("**Recomputing win path cache**");
        WINNING_PATH_CACHE.invalidateAll();
        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getNormalFinishedGamesFilter(null, null), WinningPathCacheService::addGame);
        hasBeenComputed = true;
        BotLogger.info("**Finished recomputing win path cache**");
    }

    public static synchronized void addGame(Game game) {
        game.getWinner().ifPresent(winner -> {
            CacheKey key = new CacheKey(game.getRealAndEliminatedPlayers().size(), game.getVp());
            Map<String, Integer> map = WINNING_PATH_CACHE.get(key, k -> new HashMap<>());
            String path = WinningPathHelper.buildWinningPath(game, winner);
            map.put(path, map.getOrDefault(path, 0) + 1);
        });
    }

    static synchronized Map<String, Integer> getWinningPathCounts(int playerCount, int victoryPoints) {
        if (!hasBeenComputed) recomputeCache();
        Map<String, Integer> map = WINNING_PATH_CACHE.getIfPresent(new CacheKey(playerCount, victoryPoints));
        return map == null ? Map.of() : Map.copyOf(map);
    }

    private record CacheKey(int playerCount, int victoryPoints) {}
}
