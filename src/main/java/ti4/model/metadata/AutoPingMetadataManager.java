package ti4.model.metadata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.json.JsonFileStore;

@UtilityClass
public class AutoPingMetadataManager {

    private static final JsonFileStore<AutoPings> store =
            new JsonFileStore<>("AutoPing.json", AutoPings.class, () -> new AutoPings(new HashMap<>()));
    private static final Duration MINIMUM_TIME_BETWEEN_PERSISTED_DELAYS = Duration.ofMinutes(1);
    private static final Cache<String, Boolean> recentlyDelayedGameNames = Caffeine.newBuilder()
            .expireAfterWrite(MINIMUM_TIME_BETWEEN_PERSISTED_DELAYS)
            .build();

    public static synchronized void setupAutoPing(String gameName) {
        putAutoPing(store.readOrEmpty(), gameName, 0, false);
    }

    public static synchronized void addPing(String gameName) {
        AutoPings autoPings = store.readOrEmpty();
        AutoPing autoPing = autoPings.gameNameToAutoPing.get(gameName);
        putAutoPing(autoPings, gameName, autoPing == null ? 1 : autoPing.pingCount + 1, false);
    }

    public static void delayPing(String gameName) {
        if (recentlyDelayedGameNames.asMap().putIfAbsent(gameName, Boolean.TRUE) != null) {
            return;
        }
        persistDelayedPing(gameName);
    }

    private static synchronized void persistDelayedPing(String gameName) {
        AutoPings autoPings = store.read();
        if (autoPings == null) {
            return;
        }
        AutoPing autoPing = autoPings.gameNameToAutoPing.get(gameName);
        if (autoPing == null) {
            return;
        }
        putAutoPing(autoPings, gameName, autoPing.pingCount, autoPing.quickPing);
    }

    public static synchronized void setupQuickPing(String gameName) {
        AutoPings autoPings = store.readOrEmpty();
        AutoPing autoPing = autoPings.gameNameToAutoPing.get(gameName);
        putAutoPing(autoPings, gameName, autoPing == null ? 0 : autoPing.pingCount, true);
    }

    private static void putAutoPing(AutoPings autoPings, String gameName, int pingCount, boolean quickPing) {
        autoPings.gameNameToAutoPing.put(gameName, new AutoPing(System.currentTimeMillis(), pingCount, quickPing));
        store.write(autoPings);
    }

    public static synchronized void remove(List<String> gameNames) {
        AutoPings autoPings = store.read();
        if (autoPings == null) {
            return;
        }
        gameNames.forEach(autoPings.gameNameToAutoPing::remove);
        store.write(autoPings);
    }

    @Nullable
    public static synchronized AutoPing getLatestAutoPing(String gameName) {
        return store.readOrEmpty().gameNameToAutoPing.get(gameName);
    }

    private record AutoPings(Map<String, AutoPing> gameNameToAutoPing) {}

    public record AutoPing(long lastPingTimeEpochMilliseconds, int pingCount, boolean quickPing) {}
}
