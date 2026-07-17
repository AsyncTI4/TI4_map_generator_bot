package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.service.statistics.matchmaking.queue.MatchmakerService;
import ti4.spring.service.statistics.matchmaking.queue.MatchmakingQueueSearchService;

@UtilityClass
public class MatchmakerCron {

    public static void register() {
        CronManager.schedulePeriodically(MatchmakerCron.class, MatchmakerCron::run, 15, 30, TimeUnit.MINUTES);
    }

    private static void run() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running MatchmakerCron.");
        try {
            MatchmakerService.get().processQueue();
        } catch (Exception e) {
            BotLogger.error("MatchmakerCron failed.", e);
        }
        try {
            MatchmakingQueueSearchService.get().search();
        } catch (Exception e) {
            BotLogger.error("MatchmakerCron standing search sweep failed.", e);
        }
        BotLogger.logCron("Finished MatchmakerCron.");
    }
}
