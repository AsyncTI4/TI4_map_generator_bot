package ti4.cron;

import java.time.ZoneId;

import lombok.experimental.UtilityClass;
import ti4.helpers.WebHelper;
import ti4.message.BotLogger;

@UtilityClass
public class UploadStatsCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(UploadStatsCron.class, UploadStatsCron::uploadStats, 13, 30, ZoneId.of("UTC")); // ParsleySage is currently pulling at 14:00 UTC
    }

    private static void uploadStats() {
        try {
            WebHelper.putStats();
        } catch (Exception e) {
            BotLogger.error("**UploadStatsCron failed.**", e);
        }
        BotLogger.info("Ran UploadStatsCron.");
    }
}
