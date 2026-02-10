package ti4.json;

import java.io.File;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Storage;
import ti4.message.logging.BotLogger;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@UtilityClass
public class PersistenceManager {

    private static final String PERSISTENCE_MANAGER_JSON_PATH = Storage.getStoragePath() + "/pm_json/";

    private static final JsonMapper jsonMapper =
            JsonMapper.builder().findAndAddModules().build();

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
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, object);
    }

    public static <T> T readObjectFromJsonFile(String fileName, Class<T> clazz) throws IOException {
        return readObjectFromJsonFile(PERSISTENCE_MANAGER_JSON_PATH, fileName, clazz);
    }

    public static <T> T readObjectFromJsonFile(String directory, String fileName, Class<T> clazz) throws IOException {
        JavaType ref = jsonMapper.getTypeFactory().constructType(clazz);
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

        return jsonMapper.readValue(file, clazz);
    }

    public static <T> T readObjectFromJsonFile(String directory, String fileName, TypeReference<T> typeReference)
            throws IOException {
        JavaType ref = jsonMapper.getTypeFactory().constructType(typeReference);
        return readObjectFromJsonFile(directory, fileName, ref);
    }

    public static <T> T readObjectFromJsonFile(String fileName, TypeReference<T> typeReference) throws IOException {
        return readObjectFromJsonFile(PERSISTENCE_MANAGER_JSON_PATH, fileName, typeReference);
    }

    @NotNull
    private static File getFile(String directory, String fileName) {
        return new File(directory + File.separator + fileName);
    }

    public static void deleteJsonFile(String fileName) {
        var file = getFile(PERSISTENCE_MANAGER_JSON_PATH, fileName);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                BotLogger.error("Failed to delete file: " + file.getAbsolutePath());
            }
        }
    }
}
