package ti4.commands.user;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import ti4.cron.LogCacheStatsCron;
import ti4.helpers.Storage;
import ti4.map.PersistenceManager;
import ti4.message.BotLogger;

public class UserSettingsManager {

    private static final String USER_SETTINGS_PATH = Storage.getStoragePath() + File.separator + "user_settings";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final LoadingCache<String, UserSettings> userIdToSettingsCache;

    static {
        mapper.writer(new DefaultPrettyPrinter());
        userIdToSettingsCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(2, TimeUnit.HOURS)
                .build(UserSettingsManager::load);
        LogCacheStatsCron.registerCache("userIdToSettingsCache", userIdToSettingsCache);
    }

    private static UserSettings load(String userId) {
        return readFile(userId);
    }

    public static UserSettings get(String userID) {
        var userSettings = userIdToSettingsCache.get(userID);
        if (userSettings == null) {
            userSettings = new UserSettings(userID);
            persistFile(userSettings);
            userIdToSettingsCache.put(userID, userSettings);
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
