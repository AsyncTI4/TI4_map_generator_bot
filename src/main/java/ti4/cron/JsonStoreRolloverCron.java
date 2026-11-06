package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.model.metadata.TechSummariesMetadataManager;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.service.deploy.ActiveLeaseService;

// TODO: Remove, with the file fallbacks in UserSettingsManager, AutoPingMetadataManager and
// TechSummariesMetadataManager, once every environment has imported its JSON stores into the database.
@Deprecated(forRemoval = true, since = "2026-11")
@UtilityClass
public class JsonStoreRolloverCron {

    private static volatile boolean imported;

    public static void register() {
        CronManager.schedulePeriodically(
                JsonStoreRolloverCron.class, JsonStoreRolloverCron::run, 5, 5, TimeUnit.MINUTES);
    }

    private static void run() {
        if (imported || !ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running JsonStoreRolloverCron.");
        try {
            int userSettings = UserSettingsManager.importLegacyFiles();
            int autoPings = AutoPingMetadataManager.importLegacyFile();
            int techSummaries = TechSummariesMetadataManager.importLegacyFile();
            imported = true;
            BotLogger.info("JsonStoreRolloverCron imported " + userSettings + " user settings, " + autoPings
                    + " auto pings and " + techSummaries + " tech summaries into the database.");
        } catch (Exception e) {
            BotLogger.error("JsonStoreRolloverCron failed; it will retry in 5 minutes.", e);
        }
    }
}
