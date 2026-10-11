package ti4.json;

import java.io.IOException;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import ti4.logging.BotLogger;

public final class JsonFileStore<T> {

    private final String fileName;
    private final Class<T> type;
    private final Supplier<T> emptyValue;

    public JsonFileStore(String fileName, Class<T> type, Supplier<T> emptyValue) {
        this.fileName = fileName;
        this.type = type;
        this.emptyValue = emptyValue;
    }

    @Nullable
    public T read() {
        try {
            T value = PersistenceManager.readObjectFromJsonFile(fileName, type);
            return value != null ? value : emptyValue.get();
        } catch (IOException e) {
            BotLogger.error("Failed to read json data for " + type.getSimpleName() + ".", e);
            return null;
        }
    }

    public T readOrEmpty() {
        T value = read();
        return value != null ? value : emptyValue.get();
    }

    public void write(T value) {
        try {
            PersistenceManager.writeObjectToJsonFile(fileName, value);
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for " + type.getSimpleName() + ".", e);
        }
    }
}
