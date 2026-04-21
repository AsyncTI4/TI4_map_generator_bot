package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.contest.CombatContestSelectionService;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatContestSelectionCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CombatContestSelectionCron.class,
                CombatContestSelectionCron::refreshSelectionSettings,
                30,
                600,
                TimeUnit.SECONDS);
    }

    private static void refreshSelectionSettings() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running CombatContestSelectionCron.");
        try {
            SpringContext.getBean(CombatContestSelectionService.class).recomputeAndPersistSettings();
        } catch (Exception e) {
            BotLogger.error("**CombatContestSelectionCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatContestSelectionCron.");
    }
}
