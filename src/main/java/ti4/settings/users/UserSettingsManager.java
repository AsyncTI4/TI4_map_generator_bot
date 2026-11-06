package ti4.settings.users;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.spring.service.usersettings.UserSettingsService;

@UtilityClass
public class UserSettingsManager {

    public static UserSettings get(String userId) {
        try {
            return UserSettingsService.getBean().find(userId).orElseGet(() -> new UserSettings(userId));
        } catch (RuntimeException e) {
            BotLogger.error("Failed to read user settings for user " + userId + ".", e);
            return new UserSettings(userId);
        }
    }

    public static void save(UserSettings userSettings) {
        try {
            UserSettingsLockManager.wrapWithWriteLock(
                    userSettings.getUserId(),
                    () -> UserSettingsService.getBean().save(userSettings));
        } catch (RuntimeException e) {
            BotLogger.error("Failed to save user settings for user " + userSettings.getUserId() + ".", e);
        }
    }

    static boolean addActiveHourCheckins(Map<String, int[]> checkinsByUserId) {
        Map<String, Consumer<UserSettings>> changesByUserId = new HashMap<>();
        checkinsByUserId.forEach((userId, checkins) ->
                changesByUserId.put(userId, userSettings -> userSettings.addActiveHourCheckins(checkins)));
        try {
            UserSettingsLockManager.wrapWithWriteLocks(
                    checkinsByUserId.keySet(),
                    () -> UserSettingsService.getBean().updateAll(changesByUserId, UserSettings::new));
            return true;
        } catch (RuntimeException e) {
            BotLogger.error(
                    "Failed to record active hours for " + StringHelper.pluralize(checkinsByUserId.size(), "user")
                            + ".",
                    e);
            return false;
        }
    }

    public static List<UserSettings> getAllUserSettings() {
        try {
            return UserSettingsService.getBean().findAll();
        } catch (RuntimeException e) {
            BotLogger.error("Failed to read all user settings.", e);
            return List.of();
        }
    }
}
