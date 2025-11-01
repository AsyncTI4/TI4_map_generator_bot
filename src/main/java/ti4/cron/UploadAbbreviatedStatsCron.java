package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.website.AsyncTi4WebsiteHelper;

@UtilityClass
public class UploadAbbreviatedStatsCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                UploadAbbreviatedStatsCron.class,
                UploadAbbreviatedStatsCron::uploadAbbreviatedStats,
                13,
                45,
                ZoneId.of("UTC")); // ParsleySage is currently pulling at 14:00 UTC
    }

    private static void uploadAbbreviatedStats() {
        BotLogger.logCron("Running UploadAbbreviatedStatsCron.");
        try {
            AsyncTi4WebsiteHelper.putAbbreviatedStats();
        } catch (Exception e) {
            BotLogger.error("**UploadAbbreviatedStatsCron failed.**", e);
        }
        BotLogger.logCron("Finished UploadAbbreviatedStatsCron.");
    }
}
