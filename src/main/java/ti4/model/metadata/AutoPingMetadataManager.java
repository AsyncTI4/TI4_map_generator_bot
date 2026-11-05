package ti4.model.metadata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.json.LegacyPersistenceFile;
import ti4.logging.BotLogger;
import ti4.spring.service.autoping.AutoPingService;

@UtilityClass
public class AutoPingMetadataManager {

    private static final Duration MINIMUM_TIME_BETWEEN_PERSISTED_DELAYS = Duration.ofMinutes(1);
    private static final Cache<String, Boolean> recentlyDelayedGameNames = Caffeine.newBuilder()
            .expireAfterWrite(MINIMUM_TIME_BETWEEN_PERSISTED_DELAYS)
            .build();

    // TODO: Remove the AutoPing.json fallback (this constant, databaseInUse, store, usesDatabase, importLegacyFile,
    // LegacyAutoPingFile and LegacyAutoPings) with JsonStoreRolloverCron once every environment has imported it.
    @Deprecated(forRemoval = true, since = "2026-11")
    private static final String LEGACY_AUTO_PING_FILE = "AutoPing.json";

    @Deprecated(forRemoval = true, since = "2026-11")
    private static boolean databaseInUse;

    public static void setupAutoPing(String gameName) {
        run(store -> store.compute(gameName, _ -> new AutoPing(System.currentTimeMillis(), 0, false)));
    }

    public static void addPing(String gameName) {
        run(store -> store.compute(
                gameName,
                autoPing -> new AutoPing(
                        System.currentTimeMillis(), autoPing == null ? 1 : autoPing.pingCount() + 1, false)));
    }

    public static void delayPing(String gameName) {
        if (recentlyDelayedGameNames.asMap().putIfAbsent(gameName, Boolean.TRUE) != null) {
            return;
        }
        run(store -> store.updateIfPresent(
                gameName,
                autoPing -> new AutoPing(System.currentTimeMillis(), autoPing.pingCount(), autoPing.quickPing())));
    }

    public static void setupQuickPing(String gameName) {
        run(store -> store.compute(
                gameName,
                autoPing ->
                        new AutoPing(System.currentTimeMillis(), autoPing == null ? 0 : autoPing.pingCount(), true)));
    }

    public static void remove(List<String> gameNames) {
        run(store -> store.delete(gameNames));
    }

    @Nullable
    public static AutoPing getLatestAutoPing(String gameName) {
        return call(store -> store.find(gameName).orElse(null), null);
    }

    public static Set<String> getGameNamesWithAutoPing() {
        return call(AutoPingStore::gameNames, Set.of());
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    public static synchronized int importLegacyFile() {
        AutoPingService service = AutoPingService.getBean();
        if (usesDatabase(service)) return 0;
        LegacyAutoPings legacy = LegacyPersistenceFile.read(LEGACY_AUTO_PING_FILE, LegacyAutoPings.class);
        int imported = legacy == null || legacy.gameNameToAutoPing() == null
                ? 0
                : service.importMissing(legacy.gameNameToAutoPing());
        databaseInUse = true;
        LegacyPersistenceFile.markImported(LEGACY_AUTO_PING_FILE);
        return imported;
    }

    private static void run(Consumer<AutoPingStore> operation) {
        call(
                store -> {
                    operation.accept(store);
                    return null;
                },
                null);
    }

    private static synchronized <T> T call(Function<AutoPingStore, T> operation, T fallback) {
        try {
            return operation.apply(store());
        } catch (Exception e) {
            BotLogger.error("Failed to read or write auto pings.", e);
            return fallback;
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static AutoPingStore store() {
        AutoPingService service = AutoPingService.getBean();
        return usesDatabase(service) ? new DatabaseAutoPingStore(service) : new LegacyAutoPingFile();
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static boolean usesDatabase(AutoPingService service) {
        if (!databaseInUse && (service.hasAny() || !LegacyPersistenceFile.exists(LEGACY_AUTO_PING_FILE))) {
            databaseInUse = true;
        }
        return databaseInUse;
    }

    private interface AutoPingStore {
        Optional<AutoPing> find(String gameName);

        Set<String> gameNames();

        void compute(String gameName, UnaryOperator<AutoPing> change);

        void updateIfPresent(String gameName, UnaryOperator<AutoPing> change);

        void delete(Collection<String> gameNames);
    }

    private record DatabaseAutoPingStore(AutoPingService service) implements AutoPingStore {

        public Optional<AutoPing> find(String gameName) {
            return service.find(gameName);
        }

        public Set<String> gameNames() {
            return service.gameNames();
        }

        public void compute(String gameName, UnaryOperator<AutoPing> change) {
            service.compute(gameName, change);
        }

        public void updateIfPresent(String gameName, UnaryOperator<AutoPing> change) {
            service.updateIfPresent(gameName, change);
        }

        public void delete(Collection<String> gameNames) {
            service.delete(gameNames);
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static final class LegacyAutoPingFile implements AutoPingStore {

        public Optional<AutoPing> find(String gameName) {
            return Optional.ofNullable(read().get(gameName));
        }

        public Set<String> gameNames() {
            return Set.copyOf(read().keySet());
        }

        public void compute(String gameName, UnaryOperator<AutoPing> change) {
            Map<String, AutoPing> autoPings = read();
            autoPings.compute(gameName, (_, autoPing) -> change.apply(autoPing));
            write(autoPings);
        }

        public void updateIfPresent(String gameName, UnaryOperator<AutoPing> change) {
            Map<String, AutoPing> autoPings = read();
            autoPings.computeIfPresent(gameName, (_, autoPing) -> change.apply(autoPing));
            write(autoPings);
        }

        public void delete(Collection<String> gameNames) {
            Map<String, AutoPing> autoPings = read();
            if (autoPings.keySet().removeAll(Set.copyOf(gameNames))) write(autoPings);
        }

        private static Map<String, AutoPing> read() {
            LegacyAutoPings legacy = LegacyPersistenceFile.read(LEGACY_AUTO_PING_FILE, LegacyAutoPings.class);
            return legacy == null || legacy.gameNameToAutoPing() == null
                    ? new HashMap<>()
                    : new HashMap<>(legacy.gameNameToAutoPing());
        }

        private static void write(Map<String, AutoPing> autoPings) {
            LegacyPersistenceFile.write(LEGACY_AUTO_PING_FILE, new LegacyAutoPings(autoPings));
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private record LegacyAutoPings(Map<String, AutoPing> gameNameToAutoPing) {}

    public record AutoPing(long lastPingTimeEpochMilliseconds, int pingCount, boolean quickPing) {}
}
