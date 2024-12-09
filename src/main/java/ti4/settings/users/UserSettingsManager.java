package ti4.settings.users;

import java.io.File;
import java.io.IOException;

import ti4.helpers.Storage;
import ti4.json.PersistenceManager;
import ti4.message.BotLogger;

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
            BotLogger.log("Failed to read json data for UserSettingsManager.", e);
            return null;
        }
    }

    private static void persistFile(UserSettings userSettings) {
        try {
            PersistenceManager.writeObjectToJsonFile(USER_SETTINGS_PATH, userSettings.getUserId() + ".json", userSettings);
        } catch (Exception e) {
            BotLogger.log("Failed to write json data for UserSettingsManager.", e);
        }
    }
}
