package ti4.settings.users;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.json.PersistenceManager;
import ti4.logging.BotLogger;

// TODO: Remove with JsonStoreRolloverCron once every environment has imported its user_settings files into the
// database (moved there on 2026-11-05).
@Deprecated(forRemoval = true, since = "2026-11")
@UtilityClass
class LegacyUserSettingsFiles {

    private static final String JSON_SUFFIX = ".json";
    private static volatile boolean filesFound;

    static UserSettings read(String userId) {
        try {
            UserSettings userSettings =
                    PersistenceManager.readObjectFromJsonFile(directory(), userId + JSON_SUFFIX, UserSettings.class);
            if (userSettings != null) userSettings.setUserId(userId);
            return userSettings;
        } catch (RuntimeException e) {
            BotLogger.error("User settings file for user " + userId + " could not be read.", e);
            return null;
        }
    }

    static void write(UserSettings userSettings) {
        try {
            PersistenceManager.writeObjectToJsonFile(directory(), userSettings.getUserId() + JSON_SUFFIX, userSettings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<UserSettings> readAll() {
        List<UserSettings> all = new ArrayList<>();
        for (String userId : userIdsWithFiles()) {
            UserSettings userSettings = read(userId);
            if (userSettings != null) all.add(userSettings);
        }
        return all;
    }

    static boolean anyExist() {
        if (filesFound) return true;
        Path directory = Path.of(directory());
        if (!Files.isDirectory(directory)) return false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + JSON_SUFFIX)) {
            filesFound = files.iterator().hasNext();
            return filesFound;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> userIdsWithFiles() {
        File[] files = new File(directory()).listFiles((_, name) -> name.endsWith(JSON_SUFFIX));
        if (files == null) return List.of();
        List<String> userIds = new ArrayList<>();
        for (File file : files) {
            userIds.add(file.getName().substring(0, file.getName().length() - JSON_SUFFIX.length()));
        }
        return userIds;
    }

    private static String directory() {
        return Storage.getStoragePath() + File.separator + "user_settings";
    }
}
