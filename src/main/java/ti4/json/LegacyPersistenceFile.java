package ti4.json;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.ToIntFunction;
import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

// TODO: Remove once every environment has run with AutoPing and TechSummaries in the database and pm_json holds
// only their .imported copies (moved to the database on 2026-11-05).
@Deprecated(forRemoval = true, since = "2026-11")
@UtilityClass
public class LegacyPersistenceFile {

    private static final String IMPORTED_SUFFIX = ".imported";

    public static <T> boolean importIfPresent(String fileName, Class<T> type, ToIntFunction<T> importer) {
        File file = new File(Storage.getStoragePath() + "/pm_json/" + fileName);
        if (!file.exists()) return true;
        try {
            T legacy = PersistenceManager.readObjectFromJsonFile(fileName, type);
            int imported = legacy == null ? 0 : importer.applyAsInt(legacy);
            Path source = file.toPath();
            Files.move(source, source.resolveSibling(fileName + IMPORTED_SUFFIX));
            BotLogger.info("Imported " + imported + " entries from " + fileName + " into the database.");
            return true;
        } catch (Exception e) {
            BotLogger.error("Failed to import " + fileName + " into the database; it will be retried.", e);
            return false;
        }
    }
}
