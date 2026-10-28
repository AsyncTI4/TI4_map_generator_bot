package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.settings.users.UserActiveHourRecorder;

@UtilityClass
public class FlushUserActiveHoursCron {

    public static void register() {
        CronManager.schedulePeriodically(
                FlushUserActiveHoursCron.class, FlushUserActiveHoursCron::flushUserActiveHours, 5, 5, TimeUnit.MINUTES);
    }

    private static void flushUserActiveHours() {
        try {
            UserActiveHourRecorder.flush();
        } catch (Exception e) {
            BotLogger.error("**FlushUserActiveHoursCron failed.**", e);
        }
    }
}
