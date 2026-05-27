package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.service.statistics.matchmaking.QueueForGameService;

@UtilityClass
public class QueueForGameCron {

    public static void register() {
        CronManager.schedulePeriodically(QueueForGameCron.class, QueueForGameCron::run, 5, 5, TimeUnit.MINUTES);
    }

    private static void run() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running QueueForGameCron.");
        try {
            SpringContext.getBean(QueueForGameService.class).processQueue();
        } catch (Exception e) {
            BotLogger.error("QueueForGameCron failed.", e);
        }
        BotLogger.logCron("Finished QueueForGameCron.");
    }
}
