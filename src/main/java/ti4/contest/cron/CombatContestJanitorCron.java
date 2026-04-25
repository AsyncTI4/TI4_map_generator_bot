package ti4.contest.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.service.CombatReplayJanitorService;
import ti4.cron.CronManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatContestJanitorCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CombatContestJanitorCron.class,
                CombatContestJanitorCron::runJanitor,
                120,
                TimeUnit.HOURS.toSeconds(1),
                TimeUnit.SECONDS);
    }

    private static void runJanitor() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running CombatContestJanitorCron.");
        try {
            SpringContext.getBean(CombatReplayJanitorService.class).runJanitor();
        } catch (Exception e) {
            BotLogger.error("**CombatContestJanitorCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatContestJanitorCron.");
    }
}
