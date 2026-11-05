package ti4.settings.users;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.json.PersistenceManager;
import ti4.logging.BotLogger;

// TODO: Remove once every environment has run with database-backed user settings and the user_settings
// directory holds only .imported files (moved to the database on 2026-11-05).
@Deprecated(forRemoval = true, since = "2026-11")
@UtilityClass
class LegacyUserSettingsFiles {

    private static final String JSON_SUFFIX = ".json";
    private static final String IMPORTED_SUFFIX = ".imported";
    private static final String UNREADABLE_SUFFIX = ".unreadable";

    static UserSettings read(String userId) {
        File file = file(userId);
        if (!file.exists()) return null;
        try {
            return PersistenceManager.readObjectFromJsonFile(directory(), file.getName(), UserSettings.class);
        } catch (Exception e) {
            BotLogger.error("User settings file " + file.getName() + " could not be read; setting it aside.", e);
            rename(file, UNREADABLE_SUFFIX);
            return null;
        }
    }

    static void markImported(String userId) {
        rename(file(userId), IMPORTED_SUFFIX);
    }

    static List<String> userIdsWithFiles() {
        File[] files = new File(directory()).listFiles((_, name) -> name.endsWith(JSON_SUFFIX));
        if (files == null) return List.of();
        return Arrays.stream(files)
                .map(file -> file.getName().substring(0, file.getName().length() - JSON_SUFFIX.length()))
                .toList();
    }

    private static void rename(File file, String suffix) {
        Path source = file.toPath();
        try {
            Files.move(source, source.resolveSibling(file.getName() + suffix));
        } catch (IOException e) {
            BotLogger.error("Failed to rename user settings file " + file.getName() + ".", e);
        }
    }

    private static File file(String userId) {
        return new File(directory(), userId + JSON_SUFFIX);
    }

    private static String directory() {
        return Storage.getStoragePath() + File.separator + "user_settings";
    }
}
