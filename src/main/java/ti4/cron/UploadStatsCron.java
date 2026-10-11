package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.website.GameStatisticsUploadService;

@UtilityClass
public class UploadStatsCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                UploadStatsCron.class,
                CronManager.leasedAndLogged(UploadStatsCron.class, GameStatisticsUploadService::uploadAllStats),
                13,
                30,
                ZoneId.of("UTC")); // ParsleySage is currently pulling at 14:00 UTC
    }
}
