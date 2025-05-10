package ti4.json;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Storage;
import ti4.message.BotLogger;

@UtilityClass
public class PersistenceManager {

    public static final String PERSISTENCE_MANAGER_JSON_PATH = Storage.getStoragePath() + "/pm_json/";
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public static void writeObjectToJsonFile(String fileName, Object object) throws IOException {
        writeObjectToJsonFile(PERSISTENCE_MANAGER_JSON_PATH, fileName, object);
    }

    public static void writeObjectToJsonFile(String directory, String fileName, Object object) throws IOException {
        if (object == null) {
            throw new IllegalArgumentException("The object to serialize cannot be null.");
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("The file path cannot be null or empty.");
        }

        var file = getFile(directory, fileName);
        var parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directories: " + parentDir.getAbsolutePath());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, object);
    }

    public static <T> List<T> readListFromJsonFile(String fileName, Class<T> clazz) throws IOException {
        return readListFromJsonFile(PERSISTENCE_MANAGER_JSON_PATH, fileName, clazz);
    }

    public static <T> List<T> readListFromJsonFile(String directory, String fileName, Class<T> clazz) throws IOException {
        JavaType ref = objectMapper.getTypeFactory().constructParametricType(List.class, clazz);
        return readObjectFromJsonFile(directory, fileName, ref);
    }

    public static <T> T readObjectFromJsonFile(String fileName, Class<T> clazz) throws IOException {
        return readObjectFromJsonFile(PERSISTENCE_MANAGER_JSON_PATH, fileName, clazz);
    }

    public static <T> T readObjectFromJsonFile(String directory, String fileName, Class<T> clazz) throws IOException {
        JavaType ref = objectMapper.getTypeFactory().constructType(clazz);
        return readObjectFromJsonFile(directory, fileName, ref);
    }

    private static <T> T readObjectFromJsonFile(String directory, String fileName, JavaType clazz) throws IOException {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("The file path cannot be null or empty.");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("The class type cannot be null.");
        }

        var file = getFile(directory, fileName);
        if (!file.exists()) {
            return null;
        }

        return objectMapper.readValue(file, clazz);
    }

    @NotNull
    private static File getFile(String directory, String fileName) {
        return new File(directory + File.separator + fileName);
    }

    public static void deleteJsonFile(String fileName) {
        var file = getFile(PERSISTENCE_MANAGER_JSON_PATH, fileName);
        boolean deleted = file.delete();
        if (!deleted) {
            BotLogger.error("Failed to delete file: " + file.getAbsolutePath());
        }
    }

}