package ti4.spring.api.community;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.cache.CacheManager;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;

@RequiredArgsConstructor
@Service
class CommunityStatsService {

    static final Duration STATS_TTL = Duration.ofHours(4);
    private static final String CACHE_NAME = "communityStatsCache";
    private static final String CACHE_KEY = "global";
    private static final List<String> UNAVAILABLE_METRICS = List.of("turnsThisWeek");

    private final Cache<String, CommunityStatsResponse> statsCache = createCache();

    CommunityStatsResponse get() {
        return statsCache.get(CACHE_KEY, key -> computeStats());
    }

    private static Cache<String, CommunityStatsResponse> createCache() {
        Cache<String, CommunityStatsResponse> cache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(STATS_TTL)
                .recordStats()
                .build();
        CacheManager.registerCache(CACHE_NAME, cache);
        return cache;
    }

    private static CommunityStatsResponse computeStats() {
        List<ManagedGame> games = GameManager.getManagedGames();
        long activeGames = games.stream().filter(ManagedGame::isActive).count();
        long players = games.stream()
                .flatMap(game -> game.getPlayers().stream())
                .map(ManagedPlayer::getId)
                .distinct()
                .count();
        long gamesCompleted = games.stream()
                .filter(ManagedGame::isHasEnded)
                .filter(ManagedGame::isHasWinner)
                .count();

        return new CommunityStatsResponse(
                activeGames,
                null,
                players,
                gamesCompleted,
                System.currentTimeMillis(),
                STATS_TTL.toSeconds(),
                UNAVAILABLE_METRICS);
    }
}
