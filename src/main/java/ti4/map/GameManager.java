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
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> playerNameToManagedPlayer = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> userIdToCurrentGameName = new ConcurrentHashMap<>();
    private static final LoadingCache<String, Game> activeGameCache;

    static {
        activeGameCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(2, TimeUnit.HOURS)
                .build(GameManager::loadGame);
        LogCacheStatsCron.registerCache("gameCache", activeGameCache);
    }

    public static void initialize() {
        GameSaveLoadManager.loadManagedGames()
                .forEach(managedGame -> gameNameToManagedGame.put(managedGame.getName(), managedGame));
        allGameNames.addAll(gameNameToManagedGame.keySet());
    }

    private static Game loadGame(String gameName) {
        return GameSaveLoadManager.loadGame(gameName);
    }

    public static Game getGame(String gameName) {
        if (!isValidGame(gameName)) {
            return null;
        }
        if (gameNameToManagedGame.get(gameName).isHasEnded()) {
            return loadGame(gameName);
        }
        return activeGameCache.get(gameName);
    }

    static void addOrReplaceGame(Game game) {
        allGameNames.addIfAbsent(game.getName());
        if (!hasMatchingManagedGame(game)) {
            gameNameToManagedGame.put(game.getName(), new ManagedGame(game));
        }
        if (activeGameCache.getIfPresent(game.getName()) != null) {
            activeGameCache.put(game.getName(), game);
        }
    }

    static void deleteGame(String gameName) {
        allGameNames.remove(gameName);
        gameNameToManagedGame.remove(gameName);
        activeGameCache.invalidate(gameName);
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
        return getGame(gameName);
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

    public static List<ManagedGame> getManagedGames() {
        if (gameNameToManagedGame.size() != allGameNames.size()) {
            BotLogger.log("gameNameToManagedGame size " + gameNameToManagedGame.size() +
                    " does not match allGameNames size " + allGameNames.size());
        }
        return new ArrayList<>(gameNameToManagedGame.values());
    }

    private static boolean hasMatchingManagedGame(Game game) {
        var managedGame = gameNameToManagedGame.get(game.getName());
        return managedGame != null && managedGame.matches(game);
    }

    public static ManagedPlayer getManagedPlayer(String playerId) {
        return playerNameToManagedPlayer.get(playerId);
    }

    public static ManagedPlayer addOrMergePlayer(ManagedGame game, Player player) {
        var managedPlayer = playerNameToManagedPlayer.computeIfAbsent(player.getUserID(), k -> new ManagedPlayer(game, player));
        if (!managedPlayer.getGames().contains(game)) {
            managedPlayer.merge(game, player);
        }
        return managedPlayer;
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
