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

public class GameManager {

    private static volatile GameManager instance;

    private final List<String> allGameNames = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, String> userIdToCurrentGameName = new ConcurrentHashMap<>();
    private final LoadingCache<String, Game> gameCache;

    private GameManager() {
        gameCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(2, TimeUnit.HOURS)
                .build(this::loadGame);
        allGameNames.addAll(GameSaveLoadManager.getAllGameNames());
        LogCacheStatsCron.registerCache("gameCache", gameCache);
    }

    private Game loadGame(String gameName) {
        return GameSaveLoadManager.loadGame(gameName);
    }

    public static GameManager getInstance() {
        if (instance == null) {
            synchronized (GameManager.class) {
                if (instance == null) {
                    instance = new GameManager();
                }
            }
        }
        return instance;
    }

    public void addGame(Game game) {
        allGameNames.add(game.getName());
        gameCache.put(game.getName(), game);
    }

    public Game getGame(String gameName) {
        if (!isValidGame(gameName)) {
            return null;
        }
        return gameCache.get(gameName);
    }

    public void deleteGame(String gameName) {
        allGameNames.remove(gameName);
        gameCache.invalidate(gameName);
    }

    public boolean isValidGame(String gameName) {
        return allGameNames.contains(gameName);
    }

    public boolean setGameForUser(String userId, String gameName) {
        if (isValidGame(gameName)) {
            userIdToCurrentGameName.put(userId, gameName);
            return true;
        }
        return false;
    }

    public void resetGameForUser(String userId) {
        userIdToCurrentGameName.remove(userId);
    }

    public boolean isUserWithActiveGame(String userId) {
        return userIdToCurrentGameName.containsKey(userId);
    }

    @Nullable
    public Game getUserActiveGame(String userId) {
        String gameName = userIdToCurrentGameName.get(userId);
        if (gameName == null) {
            return null;
        }
        return gameCache.get(gameName);
    }

    public List<String> getGameNames() {
        return new ArrayList<>(allGameNames);
    }

    public int getNumberOfGames() {
        return allGameNames.size();
    }

    // WARNING, THIS INVOLVES READING EVERY GAME. IT IS AN EXPENSIVE OPERATION.
    public PagedGames getGamesPage(int page) {
        var pagedGames = new PagedGames();
        for (int i = PagedGames.PAGE_SIZE * page; i < allGameNames.size() && pagedGames.getGames().size() < PagedGames.PAGE_SIZE; i++) {
            pagedGames.games.add(loadGame(allGameNames.get(i)));
        }
        pagedGames.hasNextPage = allGameNames.size() / PagedGames.PAGE_SIZE > page;
        return pagedGames;
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
