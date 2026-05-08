package ti4.service.persistence;

import lombok.experimental.UtilityClass;
import ti4.settings.GlobalSettings;

@UtilityClass
public class SqlitePersistenceGate {

    public static boolean isDisabled() {
        return GlobalSettings.ImplementedSettings.SQLITE_PERSISTENCE_DISABLED.getAsBoolean(false);
    }

    public static void setDisabled(boolean disabled) {
        GlobalSettings.setSetting(GlobalSettings.ImplementedSettings.SQLITE_PERSISTENCE_DISABLED, disabled);
    }

    public static String statusMessage() {
        if (!isDisabled()) {
            return "SQLite-backed auxiliary persistence is **ON**.";
        }
        return "SQLite-backed auxiliary persistence is **OFF**. Game commands and file saves still run, but"
                + " SQLite/JDBC-backed statistics, dashboard caches, map image metadata, message cache, and SQL"
                + " developer commands are no-op.";
    }
}
