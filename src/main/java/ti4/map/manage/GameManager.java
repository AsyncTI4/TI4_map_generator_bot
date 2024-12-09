package ti4.map.manage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.experimental.UtilityClass;
import ti4.cache.CacheManager;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;

@UtilityClass
public class GameManager {

    private static final CopyOnWriteArrayList<String> allGameNames = new CopyOnWriteArrayList<>();
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> playerNameToManagedPlayer = new ConcurrentHashMap<>();
    private static final LoadingCache<String, Game> activeGameCache;

    static {
        activeGameCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterAccess(2, TimeUnit.HOURS)
                .build(GameManager::load);
        CacheManager.registerCache("gameCache", activeGameCache);
    }

    public static void initialize() {
        GameLoadService.loadManagedGames()
                .forEach(managedGame -> gameNameToManagedGame.put(managedGame.getName(), managedGame));
        allGameNames.addAll(gameNameToManagedGame.keySet());
    }

    private static Game load(String gameName) {
        Game game = GameLoadService.load(gameName);
        if (game == null) {
            return null;
        }
        if (doesNotHaveMatchingManagedGame(game)) {
            gameNameToManagedGame.put(game.getName(), new ManagedGame(game));
        }
        return game;
    }

    private static boolean doesNotHaveMatchingManagedGame(Game game) {
        var managedGame = gameNameToManagedGame.get(game.getName());
        return managedGame == null || !managedGame.matches(game);
    }

    @Nullable
    static Game get(String gameName) {
        if (!isValid(gameName)) {
            return null;
        }
        var managedGame = gameNameToManagedGame.get(gameName);
        if (managedGame == null || managedGame.isHasEnded()) {
            activeGameCache.invalidate(gameName);
            return load(gameName);
        }
        return activeGameCache.get(gameName);
    }

    public static boolean isValid(String gameName) {
        return gameName != null && allGameNames.contains(gameName);
    }

    public static boolean save(Game game, String reason) {
        if (!GameSaveService.save(game, reason)) {
            return false;
        }
        allGameNames.addIfAbsent(game.getName());
        gameNameToManagedGame.put(game.getName(), new ManagedGame(game));
        if (!game.isHasEnded()) {
            activeGameCache.put(game.getName(), game);
        }
        return true;
    }

    public static boolean delete(String gameName) {
        if (!GameSaveService.delete(gameName)) {
            return false;
        }
        allGameNames.remove(gameName);
        var managedGame = gameNameToManagedGame.remove(gameName);
        if (managedGame != null) {
            managedGame.getPlayers().forEach(player -> player.getGames().remove(managedGame));
        }
        activeGameCache.invalidate(gameName);
        return true;
    }

    @Nullable
    public static Game undo(Game game) {
        Game undo = GameUndoService.undo(game);
        return handleUndo(undo);
    }

    private static Game handleUndo(Game undo) {
        if (undo == null) {
            return null;
        }
        activeGameCache.invalidate(undo.getName());
        if (doesNotHaveMatchingManagedGame(undo)) {
            gameNameToManagedGame.put(undo.getName(), new ManagedGame(undo));
        }
        return undo;
    }

    @Nullable
    public static Game undo(Game game, int undoIndex) {
        Game undo = GameUndoService.undo(game, undoIndex);
        return handleUndo(undo);
    }

    @Nullable
    public static Game reload(String gameName) {
        activeGameCache.invalidate(gameName);
        allGameNames.addIfAbsent(gameName);
        return get(gameName);
    }

    public static List<String> getGameNames() {
        return new ArrayList<>(allGameNames);
    }

    public static int getGameCount() {
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

    public static ManagedPlayer getManagedPlayer(String playerId) {
        return playerNameToManagedPlayer.get(playerId);
    }

    static ManagedPlayer addOrMergePlayer(ManagedGame game, Player player) {
        var managedPlayer = playerNameToManagedPlayer.computeIfAbsent(player.getUserID(), k -> new ManagedPlayer(game, player));
        if (!managedPlayer.getGames().contains(game)) {
            managedPlayer.merge(game, player);
        }
        return managedPlayer;
    }
}
