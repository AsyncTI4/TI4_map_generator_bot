package ti4.map;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import ti4.cron.LogCacheStatsCron;
import ti4.message.BotLogger;

public class GameManager {

    private static final CopyOnWriteArrayList<String> allGameNames = new CopyOnWriteArrayList<>();
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> playerNameToManagedPlayer = new ConcurrentHashMap<>();
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
            activeGameCache.invalidate(gameName);
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

    static void invalidateGame(String gameName) {
        allGameNames.remove(gameName);
        var managedGame = gameNameToManagedGame.remove(gameName);
        managedGame.getPlayers().forEach(player -> player.getGames().remove(managedGame));
        activeGameCache.invalidate(gameName);
    }

    public static boolean isValidGame(String gameName) {
        return allGameNames.contains(gameName);
    }

    public static List<String> getGameNames() {
        return new ArrayList<>(allGameNames);
    }

    public static int getNumberOfGames() {
        return allGameNames.size();
    }

    public static ManagedGame getManagedGame(String gameName) {
        return gameNameToManagedGame.get(gameName);
    }

    public static List<ManagedGame> getManagedGames() {
        if (gameNameToManagedGame.size() != allGameNames.size()) {
            BotLogger.log("gameNameToManagedGame size " + gameNameToManagedGame.size() +
                    " does not match allGameNames size " + allGameNames.size() +
                    ". Something is very off...");
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
}
