package ti4.map;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GameManager {

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

    public static List<String> getGameNames() {
        return getGameNameToGame().keySet().stream().sorted().toList();
    }
}
