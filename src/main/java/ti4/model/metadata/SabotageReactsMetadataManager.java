package ti4.model.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.experimental.UtilityClass;
import ti4.json.PersistenceManager;
import ti4.message.BotLogger;

@UtilityClass
public class SabotageReactsMetadataManager {

    private static final String SABOTAGE_AUTO_REACT_FILE = "SabotageReacts.json";

    public static synchronized void updateSabotageReacts(String gameName, String playerId, String actionCardId) {
        AllSabotageReacts allSabotageReacts = readFile();
        if (allSabotageReacts == null) {
            return;
        }

        GameSabotageReacts gameSabotageReacts = allSabotageReacts.gameNameToSabotageReacts
            .computeIfAbsent(gameName, k -> new GameSabotageReacts(new HashMap<>()));

        gameSabotageReacts.actionCardIdToPlayerReacts
            .computeIfAbsent(actionCardId, k -> new ArrayList<>())
            .add(playerId);

        persistFile(allSabotageReacts);
    }

    public static synchronized void consumeAndPersist(Consumer<AllSabotageReacts> consumer) {
        AllSabotageReacts allSabotageReacts = readFile();
        consumer.accept(allSabotageReacts);
        persistFile(allSabotageReacts);
    }

    private static AllSabotageReacts readFile() {
        try {
            var allSabotageReacts = PersistenceManager.readObjectFromJsonFile(SABOTAGE_AUTO_REACT_FILE, AllSabotageReacts.class);
            return allSabotageReacts != null ? allSabotageReacts : new AllSabotageReacts(new HashMap<>());
        } catch (IOException e) {
            BotLogger.log("Failed to read json data for AllSabotageReacts.", e);
            return null;
        }
    }

    private static void persistFile(AllSabotageReacts toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(SABOTAGE_AUTO_REACT_FILE, toPersist);
        } catch (Exception e) {
            BotLogger.log("Failed to write json data for AllSabotageReacts.", e);
        }
    }

    public record AllSabotageReacts(Map<String, GameSabotageReacts> gameNameToSabotageReacts) {}

    public record GameSabotageReacts(Map<String, List<String>> actionCardIdToPlayerReacts) {}
}
