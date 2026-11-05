package ti4.settings.users;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.usersettings.UserSettingsService;

@UtilityClass
public class UserSettingsManager {

    public static UserSettings get(String userId) {
        try {
            Optional<UserSettings> stored = UserSettingsService.getBean().find(userId);
            if (stored.isPresent()) return stored.get();
            return UserSettingsLockManager.wrapWithWriteLock(userId, () -> findOrImport(userId));
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
            UserSettingsLockManager.wrapWithWriteLock(userId, () -> {
                importLegacyFileIfPresent(userId);
                UserSettingsService.getBean()
                        .update(
                                userId,
                                () -> new UserSettings(userId),
                                userSettings -> userSettings.addActiveHourCheckins(checkinsByUtcHour));
            });
        } catch (RuntimeException e) {
            BotLogger.error("Failed to record active hours for user " + userId + ".", e);
        }
    }

    public static List<UserSettings> getAllUserSettings() {
        try {
            for (String userId : LegacyUserSettingsFiles.userIdsWithFiles()) {
                UserSettingsLockManager.wrapWithWriteLock(userId, () -> importLegacyFileIfPresent(userId));
            }
            return UserSettingsService.getBean().findAll();
        } catch (RuntimeException e) {
            BotLogger.error("Failed to read all user settings.", e);
            return List.of();
        }
    }

    private static UserSettings findOrImport(String userId) {
        UserSettingsService service = UserSettingsService.getBean();
        return service.find(userId).or(() -> importLegacyFile(userId)).orElseGet(() -> new UserSettings(userId));
    }

    // TODO: Remove with LegacyUserSettingsFiles once every environment has imported its user_settings files.
    @Deprecated(forRemoval = true, since = "2026-11")
    private static void importLegacyFileIfPresent(String userId) {
        if (UserSettingsService.getBean().find(userId).isEmpty()) {
            importLegacyFile(userId);
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static Optional<UserSettings> importLegacyFile(String userId) {
        UserSettings legacy = LegacyUserSettingsFiles.read(userId);
        if (legacy == null) return Optional.empty();
        legacy.setUserId(userId);
        UserSettingsService.getBean().insertIfAbsent(legacy);
        LegacyUserSettingsFiles.markImported(userId);
        return Optional.of(legacy);
    }
}
