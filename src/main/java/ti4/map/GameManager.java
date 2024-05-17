package ti4.map;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameManager {

    private static GameManager gameManager;
    private static final Map<String, String> userNameToGameName = new HashMap<>();
    private Map<String, Game> gameNameToGame = new HashMap<>();

    private GameManager() {
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

    public void setGameNameToGame(Map<String, Game> gameNameToGame) {
        this.gameNameToGame = gameNameToGame;
    }

    public void addGame(Game activeGame) {
        gameNameToGame.put(activeGame.getName(), activeGame);
    }

    public Game getGame(String gameName) {
        return gameNameToGame.get(gameName);
    }

    public Game deleteGame(String gameName) {
        return gameNameToGame.remove(gameName);
    }

    public boolean setGameForUser(String userID, String gameName) {
        if (gameNameToGame.get(gameName) != null) {
            userNameToGameName.put(userID, gameName);
            return true;
        }
        return false;
    }

    public void resetMapForUser(String userID) {
        userNameToGameName.remove(userID);
    }

    public boolean isUserWithActiveGame(String userID) {
        return userNameToGameName.containsKey(userID);
    }

    public Game getUserActiveGame(String userID) {
        String mapName = userNameToGameName.get(userID);
        return gameNameToGame.get(mapName);
    }

    public List<String> getGameNames() {
        return getGameNameToGame().keySet().stream().sorted().toList();
    }
}
