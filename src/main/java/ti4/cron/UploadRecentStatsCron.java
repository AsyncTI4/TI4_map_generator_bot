package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.website.GameStatisticsUploadService;

@UtilityClass
public class UploadRecentStatsCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                UploadRecentStatsCron.class,
                UploadRecentStatsCron::uploadRecentStats,
                13,
                45,
                ZoneId.of("UTC")); // ParsleySage is currently pulling at 14:00 UTC
    }

    private static void uploadRecentStats() {
        BotLogger.logCron("Running UploadRecentStatsCron.");
        try {
            GameStatisticsUploadService.uploadRecentStats();
        } catch (Exception e) {
            BotLogger.error("**UploadRecentStatsCron failed.**", e);
        }
        BotLogger.logCron("Finished UploadRecentStatsCron.");
    }
}
