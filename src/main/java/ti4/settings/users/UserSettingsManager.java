package ti4.settings.users;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.json.PersistenceManager;
import ti4.message.BotLogger;

@UtilityClass
public class UserSettingsManager {

    private static final String USER_SETTINGS_PATH = Storage.getStoragePath() + File.separator + "user_settings";

    public static UserSettings get(String userId) {
        var userSettings = readFile(userId);
        if (userSettings == null) {
            userSettings = new UserSettings(userId);
        }
        return userSettings;
    }

    public static void save(UserSettings userSettings) {
        persistFile(userSettings);
    }

    private static UserSettings readFile(String userId) {
        try {
            return PersistenceManager.readObjectFromJsonFile(USER_SETTINGS_PATH, userId + ".json", UserSettings.class);
        } catch (IOException e) {
            BotLogger.error("Failed to read json data for UserSettingsManager.", e);
            return null;
        }
    }

    private static void persistFile(UserSettings userSettings) {
        try {
            PersistenceManager.writeObjectToJsonFile(
                    USER_SETTINGS_PATH, userSettings.getUserId() + ".json", userSettings);
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for UserSettingsManager.", e);
        }
    }

    public static List<UserSettings> getAllUserSettings() {
        List<UserSettings> allUserSettings = new ArrayList<>();

        File settingsDirectory = new File(USER_SETTINGS_PATH);

        // Check if directory exists
        if (!settingsDirectory.exists() || !settingsDirectory.isDirectory()) {
            return allUserSettings;
        }

        // Get all JSON files in the directory
        File[] settingsFiles = settingsDirectory.listFiles((dir, name) -> name.endsWith(".json"));

        if (settingsFiles != null) {
            for (File file : settingsFiles) {
                try {
                    // Extract userId from filename (remove .json extension)
                    String userId = file.getName().replace(".json", "");

                    // Use existing get method to read each user's settings
                    UserSettings userSettings = get(userId);
                    if (userSettings != null) {
                        allUserSettings.add(userSettings);
                    }
                } catch (Exception e) {
                    BotLogger.error("Failed to read user settings from file: " + file.getName(), e);
                }
            }
        }
        return allUserSettings;
    }
}
