package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.website.GameStatisticsUploadService;

@UtilityClass
public class UploadStatsCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                UploadStatsCron.class,
                UploadStatsCron::uploadStats,
                13,
                30,
                ZoneId.of("UTC")); // ParsleySage is currently pulling at 14:00 UTC
    }

    private static void uploadStats() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running UploadStatsCron.");
        try {
            GameStatisticsUploadService.uploadAllStats();
        } catch (Exception e) {
            BotLogger.error("**UploadStatsCron failed.**", e);
        }
        BotLogger.logCron("Finished UploadStatsCron.");
    }
}
