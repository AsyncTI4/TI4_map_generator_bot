package ti4.settings.users;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.usersettings.UserSettingsService;

@UtilityClass
public class UserSettingsManager {

    // TODO: Remove the file fallback (ROLLOVER_LOCK, databaseInUse, onRolloverStore, usesDatabase and
    // importLegacyFiles) with JsonStoreRolloverCron once every environment has imported its user_settings files.
    @Deprecated(forRemoval = true, since = "2026-11")
    private static final ReentrantReadWriteLock ROLLOVER_LOCK = new ReentrantReadWriteLock();

    @Deprecated(forRemoval = true, since = "2026-11")
    private static volatile boolean databaseInUse;

    public static UserSettings get(String userId) {
        try {
            return onRolloverStore(
                    () -> UserSettingsService.getBean().find(userId).orElseGet(() -> new UserSettings(userId)),
                    () -> Objects.requireNonNullElseGet(
                            LegacyUserSettingsFiles.read(userId), () -> new UserSettings(userId)));
        } catch (RuntimeException e) {
            BotLogger.error("Failed to read user settings for user " + userId + ".", e);
            return new UserSettings(userId);
        }
    }

    public static void save(UserSettings userSettings) {
        try {
            UserSettingsLockManager.wrapWithWriteLock(
                    userSettings.getUserId(),
                    () -> onRolloverStore(
                            () -> {
                                UserSettingsService.getBean().save(userSettings);
                                return null;
                            },
                            () -> {
                                LegacyUserSettingsFiles.write(userSettings);
                                return null;
                            }));
        } catch (RuntimeException e) {
            BotLogger.error("Failed to save user settings for user " + userSettings.getUserId() + ".", e);
        }
    }

    static void addActiveHourCheckins(String userId, int[] checkinsByUtcHour) {
        try {
            UserSettingsLockManager.wrapWithWriteLock(
                    userId,
                    () -> onRolloverStore(
                            () -> {
                                UserSettingsService.getBean()
                                        .update(
                                                userId,
                                                () -> new UserSettings(userId),
                                                userSettings -> userSettings.addActiveHourCheckins(checkinsByUtcHour));
                                return null;
                            },
                            () -> {
                                UserSettings userSettings = Objects.requireNonNullElseGet(
                                        LegacyUserSettingsFiles.read(userId), () -> new UserSettings(userId));
                                userSettings.addActiveHourCheckins(checkinsByUtcHour);
                                LegacyUserSettingsFiles.write(userSettings);
                                return null;
                            }));
        } catch (RuntimeException e) {
            BotLogger.error("Failed to record active hours for user " + userId + ".", e);
        }
    }

    public static List<UserSettings> getAllUserSettings() {
        try {
            return onRolloverStore(() -> UserSettingsService.getBean().findAll(), LegacyUserSettingsFiles::readAll);
        } catch (RuntimeException e) {
            BotLogger.error("Failed to read all user settings.", e);
            return List.of();
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    public static int importLegacyFiles() {
        ROLLOVER_LOCK.writeLock().lock();
        try {
            if (usesDatabase()) return 0;
            int imported = UserSettingsService.getBean().importMissing(LegacyUserSettingsFiles.readAll());
            databaseInUse = true;
            return imported;
        } finally {
            ROLLOVER_LOCK.writeLock().unlock();
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static <T> T onRolloverStore(Supplier<T> database, Supplier<T> legacyFiles) {
        if (databaseInUse) return database.get();
        ROLLOVER_LOCK.readLock().lock();
        try {
            return usesDatabase() ? database.get() : legacyFiles.get();
        } finally {
            ROLLOVER_LOCK.readLock().unlock();
        }
    }

    @Deprecated(forRemoval = true, since = "2026-11")
    private static boolean usesDatabase() {
        if (!databaseInUse && (UserSettingsService.getBean().hasAnySettings() || !LegacyUserSettingsFiles.anyExist())) {
            databaseInUse = true;
        }
        return databaseInUse;
    }
}
