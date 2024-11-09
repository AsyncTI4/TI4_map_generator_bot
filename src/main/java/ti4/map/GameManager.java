package ti4.map;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import ti4.cron.LogCacheStatsCron;
import ti4.message.BotLogger;

public class GameManager {

    private static final CopyOnWriteArrayList<String> allGameNames = new CopyOnWriteArrayList<>();
    private static final ConcurrentMap<String, MinifiedGame> gameNameToMinifiedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> userIdToCurrentGameName = new ConcurrentHashMap<>();
    private static final LoadingCache<String, Game> gameCache;

    static {
        gameCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(2, TimeUnit.HOURS)
                .build(GameManager::loadGame);
        LogCacheStatsCron.registerCache("gameCache", gameCache);
    }

    public static void initialize() {
        GameSaveLoadManager.loadMinifiedGames()
                .forEach(minifiedGame -> gameNameToMinifiedGame.put(minifiedGame.getName(), minifiedGame));
        allGameNames.addAll(gameNameToMinifiedGame.keySet());
    }

    private static Game loadGame(String gameName) {
        return GameSaveLoadManager.loadGame(gameName);
    }

    public static Game getGame(String gameName) {
        if (!isValidGame(gameName)) {
            return null;
        }
        return gameCache.get(gameName);
    }

    static void addOrReplace(Game game) {
        allGameNames.addIfAbsent(game.getName());
        if (!hasMatchingMinifiedGame(game)) {
            gameNameToMinifiedGame.put(game.getName(), new MinifiedGame(game));
        }
        if (gameCache.getIfPresent(game.getName()) != null) {
            gameCache.put(game.getName(), game);
        }
    }

    static void deleteGame(String gameName) {
        allGameNames.remove(gameName);
        gameNameToMinifiedGame.remove(gameName);
        gameCache.invalidate(gameName);
    }

    public static boolean isValidGame(String gameName) {
        return allGameNames.contains(gameName);
    }

    public static boolean setGameForUser(String userId, String gameName) {
        if (isValidGame(gameName)) {
            userIdToCurrentGameName.put(userId, gameName);
            return true;
        }
        return false;
    }

    public static void resetGameForUser(String userId) {
        userIdToCurrentGameName.remove(userId);
    }

    public static boolean isUserWithActiveGame(String userId) {
        return userIdToCurrentGameName.containsKey(userId);
    }

    @Nullable
    public static Game getUserActiveGame(String userId) {
        String gameName = userIdToCurrentGameName.get(userId);
        if (gameName == null) {
            return null;
        }
        return gameCache.get(gameName);
    }

    public static List<String> getGameNames() {
        return new ArrayList<>(allGameNames);
    }

    public static int getNumberOfGames() {
        return allGameNames.size();
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public static PagedGames getGamesPage(int page) {
        var pagedGames = new PagedGames();
        for (int i = PagedGames.PAGE_SIZE * page; i < allGameNames.size() && pagedGames.getGames().size() < PagedGames.PAGE_SIZE; i++) {
            pagedGames.games.add(loadGame(allGameNames.get(i)));
        }
        pagedGames.hasNextPage = allGameNames.size() / PagedGames.PAGE_SIZE > page;
        return pagedGames;
    }

    public MinifiedGame getMinifiedGame(String gameName) {
        return gameNameToMinifiedGame.get(gameName);
    }

    public static List<MinifiedGame> getMinifiedGames() {
        if (gameNameToMinifiedGame.size() != allGameNames.size()) {
            BotLogger.log("gameNameToMinifiedGame size " + gameNameToMinifiedGame.size() +
                    " does not match allGameNames size " + allGameNames.size());
        }
        return new ArrayList<>(gameNameToMinifiedGame.values());
    }

    private static boolean hasMatchingMinifiedGame(Game game) {
        var minifiedGame = gameNameToMinifiedGame.get(game.getName());
        return minifiedGame != null && minifiedGame.matches(game);
    }

    public static class PagedGames {

        public static final int PAGE_SIZE = 200;

        @Getter
        private final List<Game> games = new ArrayList<>();
        private boolean hasNextPage;

        public boolean hasNextPage() {
            return hasNextPage;
        }
    }
}
