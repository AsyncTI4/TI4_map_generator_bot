package ti4.ai.trace;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

@UtilityClass
public class AiLogStore {

    private static final String FILE_SUFFIX = ".properties";
    private static final Map<String, Map<String, String>> LOGS = new ConcurrentHashMap<>();
    private static volatile Path directoryOverride;
    private static volatile boolean inMemoryOnly;

    public static String get(String gameName, String key) {
        Map<String, String> log = logOf(gameName);
        synchronized (log) {
            return log.getOrDefault(key, "");
        }
    }

    public static void put(String gameName, String key, String value) {
        Map<String, String> log = logOf(gameName);
        synchronized (log) {
            log.put(key, value);
            write(gameName, log);
        }
    }

    public static void forget(String gameName) {
        LOGS.remove(gameName);
    }

    public static void delete(String gameName) {
        forget(gameName);
        Path file = fileOf(gameName);
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            BotLogger.error("Could not delete the AI log of " + gameName, e);
        }
    }

    public static void deleteLogsOfMissingGames(Predicate<String> gameExists) {
        Path directory = directoryOf();
        if (directory == null || !Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(file -> file.getFileName().toString().endsWith(FILE_SUFFIX))
                    .map(AiLogStore::gameNameOf)
                    .filter(gameName -> !gameExists.test(gameName))
                    .forEach(AiLogStore::delete);
        } catch (IOException e) {
            BotLogger.error("Could not look for AI logs of deleted games", e);
        }
    }

    public static void useDirectory(Path directory) {
        directoryOverride = directory;
        inMemoryOnly = false;
        LOGS.clear();
    }

    public static void keepInMemory() {
        directoryOverride = null;
        inMemoryOnly = true;
        LOGS.clear();
    }

    private static Map<String, String> logOf(String gameName) {
        return LOGS.computeIfAbsent(gameName, AiLogStore::read);
    }

    private static Path fileOf(String gameName) {
        Path directory = directoryOf();
        return directory == null ? null : directory.resolve(gameName + FILE_SUFFIX);
    }

    private static Path directoryOf() {
        if (inMemoryOnly) return null;
        Path directory = directoryOverride;
        if (directory != null) return directory;
        String storage = Storage.getStoragePath();
        return storage == null ? null : Path.of(storage, "ai_logs");
    }

    private static String gameNameOf(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.substring(0, fileName.length() - FILE_SUFFIX.length());
    }

    private static Map<String, String> read(String gameName) {
        Map<String, String> log = new TreeMap<>();
        Path file = fileOf(gameName);
        if (file == null || !Files.exists(file)) return log;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            properties.stringPropertyNames().forEach(key -> log.put(key, properties.getProperty(key)));
        } catch (IOException e) {
            BotLogger.error("Could not read the AI log of " + gameName, e);
        }
        return log;
    }

    private static void write(String gameName, Map<String, String> log) {
        Path file = fileOf(gameName);
        if (file == null) return;
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Properties properties = new Properties();
            properties.putAll(log);
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, null);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            BotLogger.error("Could not write the AI log of " + gameName, e);
        }
    }
}
