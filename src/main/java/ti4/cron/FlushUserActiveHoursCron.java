package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.settings.users.UserActiveHourRecorder;

@UtilityClass
public class FlushUserActiveHoursCron {

    public static void register() {
        CronManager.schedulePeriodically(
                FlushUserActiveHoursCron.class, UserActiveHourRecorder::flush, 5, 5, TimeUnit.MINUTES);
    }
}
