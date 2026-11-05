package ti4.json;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;

// TODO: Remove with JsonStoreRolloverCron once every environment has imported AutoPing.json and
// TechSummaries.json into the database (moved there on 2026-11-05).
@Deprecated(forRemoval = true, since = "2026-11")
@UtilityClass
public class LegacyPersistenceFile {

    private static final String IMPORTED_SUFFIX = ".imported";

    public static boolean exists(String fileName) {
        return file(fileName).exists();
    }

    public static <T> T read(String fileName, Class<T> type) {
        try {
            return PersistenceManager.readObjectFromJsonFile(fileName, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void write(String fileName, Object value) {
        try {
            PersistenceManager.writeObjectToJsonFile(fileName, value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void markImported(String fileName) {
        Path source = file(fileName).toPath();
        try {
            Files.move(source, source.resolveSibling(fileName + IMPORTED_SUFFIX));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static File file(String fileName) {
        return new File(Storage.getStoragePath() + "/pm_json/" + fileName);
    }
}
