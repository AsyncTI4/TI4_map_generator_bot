package ti4.service.persistence;

import lombok.experimental.UtilityClass;
import ti4.settings.GlobalSettings;

@UtilityClass
public class DatabasePersistenceGate {

    public static boolean isDisabled() {
        return GlobalSettings.ImplementedSettings.SQLITE_PERSISTENCE_DISABLED.getAsBoolean(false);
    }

    public static void setDisabled(boolean disabled) {
        GlobalSettings.setSetting(GlobalSettings.ImplementedSettings.SQLITE_PERSISTENCE_DISABLED, disabled);
    }

    public static String statusMessage() {
        if (!isDisabled()) {
            return "Database maintenance mode is **OFF**. Database-backed features are enabled.";
        }
        return "Database maintenance mode is **ON**. Game commands and file saves still run, but statistics, dashboard data,"
                + " map image metadata, message cache, and SQL developer tools are temporarily paused.";
    }
}
