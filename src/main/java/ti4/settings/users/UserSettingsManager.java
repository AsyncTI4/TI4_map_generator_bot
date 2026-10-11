package ti4.settings.users;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.json.PersistenceManager;
import ti4.logging.BotLogger;

@UtilityClass
public class UserSettingsManager {

    private static final String USER_SETTINGS_PATH = Storage.getStoragePath() + File.separator + "user_settings";

    public static UserSettings get(String userId) {
        return Objects.requireNonNullElseGet(readFile(userId), () -> new UserSettings(userId));
    }

    public static void save(UserSettings userSettings) {
        try {
            UserSettingsFileLockManager.wrapWithWriteLock(
                    userSettings.getUserId(), () -> writeFileWithoutLock(userSettings));
        } catch (UncheckedIOException e) {
            BotLogger.error("Failed to write json data for UserSettingsManager.", e);
        }
    }

    static void addActiveHourCheckins(String userId, int[] checkinsByUtcHour) {
        try {
            UserSettingsFileLockManager.wrapWithWriteLock(userId, () -> {
                UserSettings userSettings =
                        Objects.requireNonNullElseGet(readFileWithoutLock(userId), () -> new UserSettings(userId));
                userSettings.addActiveHourCheckins(checkinsByUtcHour);
                writeFileWithoutLock(userSettings);
            });
        } catch (RuntimeException e) {
            BotLogger.error("Failed to record active hours for user " + userId + ".", e);
        }
    }

    private static UserSettings readFile(String userId) {
        return UserSettingsFileLockManager.wrapWithReadLock(userId, () -> readFileWithoutLock(userId));
    }

    private static UserSettings readFileWithoutLock(String userId) {
        return PersistenceManager.readObjectFromJsonFile(USER_SETTINGS_PATH, userId + ".json", UserSettings.class);
    }

    private static void writeFileWithoutLock(UserSettings userSettings) {
        try {
            PersistenceManager.writeObjectToJsonFile(
                    USER_SETTINGS_PATH, userSettings.getUserId() + ".json", userSettings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<UserSettings> getAllUserSettings() {
        File settingsDirectory = new File(USER_SETTINGS_PATH);
        if (!settingsDirectory.exists() || !settingsDirectory.isDirectory()) {
            return Collections.emptyList();
        }

        File[] settingsFiles = settingsDirectory.listFiles((dir, name) -> name.endsWith(".json"));
        if (settingsFiles == null) return Collections.emptyList();

        List<UserSettings> allUserSettings = new ArrayList<>();
        for (File file : settingsFiles) {
            try {
                String userId = file.getName().replace(".json", "");
                UserSettings userSettings = readFile(userId);
                if (userSettings != null) {
                    allUserSettings.add(userSettings);
                }
            } catch (Exception e) {
                BotLogger.error("Failed to read user settings from file: " + file.getName(), e);
            }
        }

        return allUserSettings;
    }
}
