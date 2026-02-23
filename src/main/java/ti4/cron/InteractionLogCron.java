package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogBufferManager;

@UtilityClass
public class InteractionLogCron {

    public static void register() {
        BotLogger.info("Registering bot log cron");
        CronManager.schedulePeriodically(
                InteractionLogCron.class, LogBufferManager::sendBufferedLogsToDiscord, 2, 2, TimeUnit.MINUTES);
    }
}
