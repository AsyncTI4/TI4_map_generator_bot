package ti4.service.persistence;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.settings.GlobalSettings;

@UtilityClass
public class DatabasePersistenceGate {

    public static boolean isDisabled() {
        if (isLocalFallbackDatabase()) {
            return true;
        }
        return GlobalSettings.ImplementedSettings.SQLITE_PERSISTENCE_DISABLED.getAsBoolean(false);
    }

    public static void setDisabled(boolean disabled) {
        GlobalSettings.setSetting(GlobalSettings.ImplementedSettings.SQLITE_PERSISTENCE_DISABLED, disabled);
    }

    public static String statusMessage() {
        if (!isDisabled()) {
            return "Database maintenance mode is **OFF**. Database-backed features are enabled.";
        }
        String source = isLocalFallbackDatabase() ? " because no external datasource is configured" : "";
        return "Database maintenance mode is **ON**"
                + source
                + ". Game commands and file saves still run, but statistics, dashboard data,"
                + " map image metadata, message cache, and SQL developer tools are temporarily paused.";
    }

    private static boolean isLocalFallbackDatabase() {
        return StringUtils.isBlank(System.getenv("SPRING_DATASOURCE_URL"));
    }
}
