package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.website.GameStatisticsUploadService;

@UtilityClass
public class UploadRecentStatsCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                UploadRecentStatsCron.class,
                CronManager.leasedAndLogged(
                        UploadRecentStatsCron.class, GameStatisticsUploadService::uploadRecentStats),
                13,
                45,
                ZoneId.of("UTC")); // ParsleySage is currently pulling at 14:00 UTC
    }
}
