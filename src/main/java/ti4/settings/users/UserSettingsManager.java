package ti4.settings.users;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import ti4.cache.CacheManager;
import ti4.helpers.Storage;
import ti4.json.PersistenceManager;
import ti4.message.BotLogger;

public class UserSettingsManager {

    private static final String USER_SETTINGS_PATH = Storage.getStoragePath() + File.separator + "user_settings";
    private static final LoadingCache<String, UserSettings> userIdToSettingsCache;

    static {
        userIdToSettingsCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(4, TimeUnit.HOURS)
                .build(UserSettingsManager::load);
        CacheManager.registerCache("userIdToSettingsCache", userIdToSettingsCache);
    }

    private static UserSettings load(String userId) {
        return readFile(userId);
    }

    public static UserSettings get(String userId) {
        var userSettings = userIdToSettingsCache.get(userId);
        if (userSettings == null) {
            userSettings = new UserSettings(userId);
            persistFile(userSettings);
            userIdToSettingsCache.put(userId, userSettings);
        }
        return userSettings;
    }

    public static void save(UserSettings userSettings) {
        userIdToSettingsCache.put(userSettings.getUserId(), userSettings);
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
