package ti4.service.statistics.game;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.experimental.UtilityClass;
import ti4.cache.CacheManager;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.GamesPage;

import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class WinningPathCacheService {

    private static final Cache<CacheKey, Map<String, Integer>> WINNING_PATH_CACHE = Caffeine.newBuilder()
            .recordStats()
            .build();

    static {
        CacheManager.registerCache("winningPathCache", WINNING_PATH_CACHE);
    }

    public static void recomputeCache() {
        WINNING_PATH_CACHE.invalidateAll();
        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getNormalFinishedGamesFilter(null, null),
                game -> addGame(game)
        );
    }

    public static void addGame(Game game) {
        game.getWinner().ifPresent(winner -> {
            CacheKey key = new CacheKey(game.getRealAndEliminatedPlayers().size(), game.getVp());
            Map<String, Integer> map = WINNING_PATH_CACHE.get(key, k -> new HashMap<>());
            String path = WinningPathHelper.buildWinningPath(game, winner);
            map.put(path, map.getOrDefault(path, 0) + 1);
        });
    }

    public static Map<String, Integer> getWinningPathCounts(int playerCount, int victoryPoints) {
        Map<String, Integer> map = WINNING_PATH_CACHE.getIfPresent(new CacheKey(playerCount, victoryPoints));
        return map == null ? Map.of() : Map.copyOf(map);
    }

    private record CacheKey(int playerCount, int victoryPoints) {
    }
}
