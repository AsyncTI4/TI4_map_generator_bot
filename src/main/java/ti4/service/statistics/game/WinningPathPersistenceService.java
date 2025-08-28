package ti4.service.statistics.game;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.json.PersistenceManager;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.logging.BotLogger;

@UtilityClass
public class WinningPathPersistenceService {

    private static final String WINNING_PATHS_FILE = "winningPaths.json";

    public static synchronized void recomputeFile() {
        BotLogger.info("**Recomputing win paths file**");
        Map<String, Map<String, Integer>> data = new HashMap<>();
        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getNormalFinishedGamesFilter(null, null), game -> computeWinPath(game, data));
        writeData(data);
        BotLogger.info("**Finished recomputing win paths file**");
    }

    private static void computeWinPath(Game game, Map<String, Map<String, Integer>> data) {
        game.getWinner().ifPresent(winner -> {
            String key = key(game.getRealAndEliminatedPlayers().size(), game.getVp());
            Map<String, Integer> map = data.computeIfAbsent(key, k -> new HashMap<>());
            String path = WinningPathHelper.buildWinningPath(game, winner);
            map.put(path, map.getOrDefault(path, 0) + 1);
        });
    }

    public static synchronized void addGame(Game game) {
        game.getWinner().ifPresent(winner -> {
            Map<String, Map<String, Integer>> data = readData();
            computeWinPath(game, data);
            writeData(data);
        });
    }

    static synchronized Map<String, Integer> getWinningPathCounts(int playerCount, int victoryPoints) {
        Map<String, Map<String, Integer>> data = readData();
        Map<String, Integer> map = data.get(key(playerCount, victoryPoints));
        return map == null ? Collections.emptyMap() : map;
    }

    private static Map<String, Map<String, Integer>> readData() {
        try {
            Map<String, Map<String, Integer>> data =
                    PersistenceManager.readObjectFromJsonFile(WINNING_PATHS_FILE, new TypeReference<>() {});
            return data == null ? new HashMap<>() : data;
        } catch (IOException e) {
            BotLogger.error("Failed to read winning paths file", e);
            return new HashMap<>();
        }
    }

    private static void writeData(Map<String, Map<String, Integer>> data) {
        try {
            PersistenceManager.writeObjectToJsonFile(WINNING_PATHS_FILE, data);
        } catch (IOException e) {
            BotLogger.error("Failed to write winning paths file", e);
        }
    }

    private static String key(int playerCount, int victoryPoints) {
        return playerCount + "_" + victoryPoints;
    }
}
