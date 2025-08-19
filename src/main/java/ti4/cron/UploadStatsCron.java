package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.website.AsyncTi4WebsiteHelper;

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
        BotLogger.info("Running UploadStatsCron.");
        try {
            AsyncTi4WebsiteHelper.putStats();
        } catch (Exception e) {
            BotLogger.error("**UploadStatsCron failed.**", e);
        }
        BotLogger.info("Finished UploadStatsCron.");
    }
}
