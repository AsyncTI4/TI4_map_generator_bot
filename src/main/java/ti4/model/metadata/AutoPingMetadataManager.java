package ti4.model.metadata;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import ti4.json.PersistenceManager;
import ti4.message.BotLogger;

@UtilityClass
public class AutoPingMetadataManager {

    private static final String AUTO_PING_FILE = "AutoPing.json";

    public static synchronized void addPing(String gameName) {
        AutoPings autoPings = readFile();
        if (autoPings == null) {
            autoPings = new AutoPings(new HashMap<>());
        }

        AutoPing autoPing = autoPings.gameNameToAutoPing.get(gameName);
        if (autoPing == null) {
            autoPings.gameNameToAutoPing.put(gameName, new AutoPing(Instant.now(), 1));
        } else {
            autoPings.gameNameToAutoPing.put(gameName, new AutoPing(Instant.now(), autoPing.pingCount + 1));
        }

        persistFile(autoPings);
    }

    public static synchronized void remove(List<String> gameNames) {
        AutoPings autoPings = readFile();
        if (autoPings == null) {
            return;
        }

        gameNames.forEach(autoPings.gameNameToAutoPing::remove);

        persistFile(autoPings);
    }

    public static AutoPing getLatestAutoPing(String gameName) {
        AutoPings autoPings = readFile();
        if (autoPings == null) {
            return null;
        }

        return autoPings.gameNameToAutoPing.get(gameName);
    }

    private static AutoPings readFile() {
        try {
            AutoPings autoPings = PersistenceManager.readObjectFromJsonFile(AUTO_PING_FILE, AutoPings.class);
            return autoPings != null ? autoPings : new AutoPings(new HashMap<>());
        } catch (IOException e) {
            BotLogger.log("Failed to read json data for AutoPings.", e);
            return null;
        }
    }

    private static void persistFile(AutoPings toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(AUTO_PING_FILE, toPersist);
        } catch (Exception e) {
            BotLogger.log("Failed to write json data for AutoPings.", e);
        }
    }

    private record AutoPings(Map<String, AutoPing> gameNameToAutoPing) {}

    public record AutoPing(Instant lastPingTime, int pingCount) {}
}
