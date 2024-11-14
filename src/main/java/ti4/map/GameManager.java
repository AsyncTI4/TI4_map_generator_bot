package ti4.map;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class GameManager {

    private static final ConcurrentMap<String, String> userNameToGameName = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Game> gameNameToGame = new ConcurrentHashMap<>();

    public static Map<String, Game> getGameNameToGame() {
        return gameNameToGame;
    }

    public static void addGame(Game game) {
        gameNameToGame.put(game.getName(), game);
    }

    public static Game getGame(String gameName) {
        return gameNameToGame.get(gameName);
    }

    public static Game deleteGame(String gameName) {
        return gameNameToGame.remove(gameName);
    }

    public static boolean isValidGame(String game) {
        return gameNameToGame.containsKey(game);
    }

    public static boolean setGameForUser(String userID, String gameName) {
        if (gameNameToGame.get(gameName) != null) {
            userNameToGameName.put(userID, gameName);
            return true;
        }
        return false;
    }

    public static void resetGameForUser(String userID) {
        userNameToGameName.remove(userID);
    }

    public static boolean isUserWithActiveGame(String userID) {
        return userNameToGameName.containsKey(userID);
    }

    @Nullable
    public static Game getUserActiveGame(String userID) {
        String mapName = userNameToGameName.get(userID);
        if (mapName == null) {
            return null;
        }
        return gameNameToGame.get(mapName);
    }

    public static List<String> getGameNames() {
        return getGameNameToGame().keySet().stream().sorted().toList();
    }
}
