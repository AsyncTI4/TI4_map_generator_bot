package ti4.cron;

import java.time.ZoneId;

import lombok.experimental.UtilityClass;
import ti4.helpers.WebHelper;
import ti4.message.BotLogger;

@UtilityClass
public class UploadStatsCron {

    public static void register() {
        CronManager.register(UploadStatsCron.class, UploadStatsCron::uploadStats, 0, 0, ZoneId.of("America/New_York"));
    }

    private static void uploadStats() {
        try {
            WebHelper.putStats();
        } catch (Exception e) {
            BotLogger.log("**UploadStatsCron failed.**", e);
        }
        BotLogger.log("Ran UploadStatsCron.");
    }
}
