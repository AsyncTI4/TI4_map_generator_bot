package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.logging.LogBufferManager;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class InteractionLogCron {

    public static void register() {
        BotLogger.info("Registering bot log cron");
        CronManager.schedulePeriodically(
                InteractionLogCron.class, InteractionLogCron::sendBufferedLogsToDiscord, 2, 2, TimeUnit.MINUTES);
    }

    private static void sendBufferedLogsToDiscord() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        LogBufferManager.sendBufferedLogsToDiscord();
    }
}
