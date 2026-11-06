package ti4.settings.users;

import java.util.List;
import lombok.experimental.UtilityClass;
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

    static void addActiveHourCheckins(String userId, int[] checkinsByUtcHour) {
        try {
            UserSettingsLockManager.wrapWithWriteLock(
                    userId,
                    () -> UserSettingsService.getBean()
                            .update(
                                    userId,
                                    () -> new UserSettings(userId),
                                    userSettings -> userSettings.addActiveHourCheckins(checkinsByUtcHour)));
        } catch (RuntimeException e) {
            BotLogger.error("Failed to record active hours for user " + userId + ".", e);
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
