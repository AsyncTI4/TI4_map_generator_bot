package ti4.settings.users;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.usersettings.UserSettingsService;

@UtilityClass
public class FogViewSettings {

    public static final String DEFAULT_FILTER = "default";
    public static final int DEFAULT_REACH = 2;

    public record Prefs(String filter, boolean ghostHexes, boolean boxes, int reach, boolean codes) {

        public static final Prefs DEFAULT = new Prefs(DEFAULT_FILTER, true, true, DEFAULT_REACH, true);

        static Prefs of(UserSettings settings) {
            return new Prefs(
                    settings.getFogFilter() == null ? DEFAULT_FILTER : settings.getFogFilter(),
                    settings.isFogGhostHexes(),
                    settings.isFogOverviewBoxes(),
                    settings.getFogOverviewReach(),
                    settings.isFogOverviewCodes());
        }

        public Prefs withFilter(String value) {
            return new Prefs(value, ghostHexes, boxes, reach, codes);
        }

        public Prefs withGhostHexes(boolean value) {
            return new Prefs(filter, value, boxes, reach, codes);
        }

        public Prefs withBoxes(boolean value) {
            return new Prefs(filter, ghostHexes, value, reach, codes);
        }

        public Prefs withReach(int value) {
            return new Prefs(filter, ghostHexes, boxes, value, codes);
        }

        public Prefs withCodes(boolean value) {
            return new Prefs(filter, ghostHexes, boxes, reach, value);
        }
    }

    private static final Map<String, Prefs> PREFS_BY_USER = new ConcurrentHashMap<>();

    public static Prefs of(String userId) {
        if (userId == null) {
            return Prefs.DEFAULT;
        }
        return PREFS_BY_USER.computeIfAbsent(userId, id -> Prefs.of(UserSettingsManager.get(id)));
    }

    public static void change(String userId, UnaryOperator<Prefs> change) {
        if (userId == null) {
            return;
        }
        Prefs changed = change.apply(of(userId));
        PREFS_BY_USER.put(userId, changed);
        store(userId, settings -> {
            settings.setFogFilter(changed.filter());
            settings.setFogGhostHexes(changed.ghostHexes());
            settings.setFogOverviewBoxes(changed.boxes());
            settings.setFogOverviewReach(changed.reach());
            settings.setFogOverviewCodes(changed.codes());
        });
    }

    private static void store(String userId, Consumer<UserSettings> change) {
        try {
            UserSettingsLockManager.wrapWithWriteLock(
                    userId, () -> UserSettingsService.getBean().update(userId, () -> new UserSettings(userId), change));
        } catch (RuntimeException e) {
            BotLogger.error("Failed to save fog view settings for user " + userId + ".", e);
        }
    }
}
