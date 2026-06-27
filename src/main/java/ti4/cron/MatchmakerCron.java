package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.service.statistics.matchmaking.queue.MatchmakerService;

@UtilityClass
public class MatchmakerCron {

    public static void register() {
        CronManager.schedulePeriodically(MatchmakerCron.class, MatchmakerCron::run, 10, 10, TimeUnit.MINUTES);
    }

    private static void run() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running MatchmakerCron.");
        try {
            SpringContext.getBean(MatchmakerService.class).processQueue();
        } catch (Exception e) {
            BotLogger.error("MatchmakerCron failed.", e);
        }
        BotLogger.logCron("Finished MatchmakerCron.");
    }
}
