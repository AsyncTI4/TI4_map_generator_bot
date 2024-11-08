package ti4.map;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jetbrains.annotations.Nullable;

public class GameManager {

    private final long loadTime;
    private static GameManager gameManager;
    private static final ConcurrentMap<String, String> userNameToGameName = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Game> gameNameToGame = new ConcurrentHashMap<>();

    private GameManager() {
        loadTime = System.currentTimeMillis();
    }

    public static GameManager getInstance() {
        if (gameManager == null) {
            gameManager = new GameManager();
        }
        return gameManager;
    }

    public Map<String, Game> getGameNameToGame() {
        return gameNameToGame;
    }

    public void addGame(Game game) {
        gameNameToGame.put(game.getName(), game);
    }

    public Game getGame(String gameName) {
        return gameNameToGame.get(gameName);
    }

    public Game deleteGame(String gameName) {
        return gameNameToGame.remove(gameName);
    }

    public boolean isValidGame(String game) {
        return gameNameToGame.containsKey(game);
    }

    public boolean setGameForUser(String userID, String gameName) {
        if (gameNameToGame.get(gameName) != null) {
            userNameToGameName.put(userID, gameName);
            return true;
        }
        return false;
    }

    public void resetGameForUser(String userID) {
        userNameToGameName.remove(userID);
    }

    public boolean isUserWithActiveGame(String userID) {
        return userNameToGameName.containsKey(userID);
    }

    @Nullable
    public Game getUserActiveGame(String userID) {
        String mapName = userNameToGameName.get(userID);
        if (mapName == null) {
            return null;
        }
        return gameNameToGame.get(mapName);
    }

    public List<String> getGameNames() {
        return getGameNameToGame().keySet().stream().sorted().toList();
    }

    public long getLoadTime() {
        return loadTime;
    }
}
