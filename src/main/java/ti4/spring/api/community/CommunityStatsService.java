package ti4.spring.api.community;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.cache.CacheManager;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;

@RequiredArgsConstructor
@Service
class CommunityStatsService {

    static final Duration STATS_TTL = Duration.ofHours(4);
    private static final String CACHE_NAME = "communityStatsCache";
    private static final String CACHE_KEY = "global";
    private static final List<String> UNAVAILABLE_METRICS = List.of();
    private static final int SAMPLE_SIZE = 4;
    private static final Pattern PBD_NAME_PATTERN = Pattern.compile("(?i)^pbd(\\d+)$");

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
        List<ManagedGame> activeGameList =
                games.stream().filter(ManagedGame::isActive).toList();
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
        List<CommunityGameSample> gamesInProgress = sampleGamesInProgress(activeGameList);

        return new CommunityStatsResponse(
                activeGames,
                players,
                gamesCompleted,
                gamesInProgress,
                System.currentTimeMillis(),
                STATS_TTL.toSeconds(),
                UNAVAILABLE_METRICS);
    }

    private static List<CommunityGameSample> sampleGamesInProgress(List<ManagedGame> activeGameList) {
        List<ManagedGame> shuffled = new ArrayList<>(activeGameList);
        Collections.shuffle(shuffled);

        return shuffled.stream()
                .limit(SAMPLE_SIZE)
                .map(CommunityStatsService::toGameSample)
                .toList();
    }

    private static CommunityGameSample toGameSample(ManagedGame managedGame) {
        Game game = managedGame.getGame();
        if (game == null) {
            return new CommunityGameSample(managedGame.getName(), managedGame.getName(), 0, 10, List.of());
        }

        List<String> factions = game.getRealPlayers().stream()
                .map(Player::getFaction)
                .filter(StringUtils::isNotBlank)
                .toList();

        return new CommunityGameSample(
                game.getName(), formatGameName(game.getName()), game.getRound(), game.getVp(), factions);
    }

    private static String formatGameName(String gameName) {
        var matcher = PBD_NAME_PATTERN.matcher(gameName);
        if (matcher.matches()) {
            return "PBD " + matcher.group(1);
        }
        return gameName;
    }
}
