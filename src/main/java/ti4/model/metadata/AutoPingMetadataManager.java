package ti4.model.metadata;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import ti4.json.PersistenceManager;
import ti4.message.BotLogger;

@UtilityClass
public class AutoPingMetadataManager {

    private static final String AUTO_PING_FILE = "AutoPing.json";

    public static synchronized void setupAutoPing(String gameName) {
        AutoPings autoPings = readFile();
        if (autoPings == null) {
            autoPings = new AutoPings(new HashMap<>());
        }

        autoPings.gameNameToAutoPing.put(gameName, new AutoPing(System.currentTimeMillis(), 0, false));

        persistFile(autoPings);
    }

    public static synchronized void addPing(String gameName) {
        AutoPings autoPings = readFile();
        if (autoPings == null) {
            autoPings = new AutoPings(new HashMap<>());
        }

        AutoPing autoPing = autoPings.gameNameToAutoPing.get(gameName);
        if (autoPing == null) {
            autoPings.gameNameToAutoPing.put(gameName, new AutoPing(System.currentTimeMillis(), 1, false));
        } else {
            autoPings.gameNameToAutoPing.put(gameName, new AutoPing(System.currentTimeMillis(), autoPing.pingCount + 1, false));
        }

        persistFile(autoPings);
    }

    public static synchronized void delayPing(String gameName) {
        AutoPings autoPings = readFile();
        if (autoPings == null) {
            return;
        }

        AutoPing autoPing = autoPings.gameNameToAutoPing.get(gameName);
        if (autoPing == null) {
            return;
        }

        autoPings.gameNameToAutoPing.put(gameName, new AutoPing(System.currentTimeMillis(), autoPing.pingCount, autoPing.quickPing));

        persistFile(autoPings);
    }

    public static synchronized void setupQuickPing(String gameName) {
        AutoPings autoPings = readFile();
        if (autoPings == null) {
            autoPings = new AutoPings(new HashMap<>());
        }

        AutoPing autoPing = autoPings.gameNameToAutoPing.get(gameName);
        if (autoPing == null) {
            autoPings.gameNameToAutoPing.put(gameName, new AutoPing(System.currentTimeMillis(), 0, true));
        } else {
            autoPings.gameNameToAutoPing.put(gameName, new AutoPing(System.currentTimeMillis(), autoPing.pingCount, true));
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

    @Nullable
    public static synchronized AutoPing getLatestAutoPing(String gameName) {
        AutoPings autoPings = readFile();
        if (autoPings == null) {
            autoPings = new AutoPings(new HashMap<>());
        }

        return autoPings.gameNameToAutoPing.get(gameName);
    }

    private static AutoPings readFile() {
        try {
            AutoPings autoPings = PersistenceManager.readObjectFromJsonFile(AUTO_PING_FILE, AutoPings.class);
            return autoPings != null ? autoPings : new AutoPings(new HashMap<>());
        } catch (IOException e) {
            BotLogger.error("Failed to read json data for AutoPings.", e);
            return null;
        }
    }

    private static void persistFile(AutoPings toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(AUTO_PING_FILE, toPersist);
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for AutoPings.", e);
        }
    }

    private record AutoPings(Map<String, AutoPing> gameNameToAutoPing) {}

    public record AutoPing(long lastPingTimeEpochMilliseconds, int pingCount, boolean quickPing) {}
}
