package ti4.map;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Storage;

public class PersistenceManager {

    public static final String PERSISTENCE_MANAGER_JSON_PATH = "/pm_json/";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void writeObjectToJsonFile(String fileName, Object object) throws IOException {
        if (object == null) {
            throw new IllegalArgumentException("The object to serialize cannot be null.");
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("The file path cannot be null or empty.");
        }

        var file = getFile(fileName);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, object);
    }

    public static <T> T readObjectFromJsonFile(String fileName, Class<T> clazz) throws IOException {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("The file path cannot be null or empty.");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("The class type cannot be null.");
        }

        var file = getFile(fileName);
        if (!file.exists()) {
            throw new IOException("File not found: " + fileName);
        }

        return objectMapper.readValue(file, clazz);
    }

    @NotNull
    private static File getFile(String fileName) {
        return new File(Storage.getStoragePath() + PERSISTENCE_MANAGER_JSON_PATH + fileName);
    }

}
