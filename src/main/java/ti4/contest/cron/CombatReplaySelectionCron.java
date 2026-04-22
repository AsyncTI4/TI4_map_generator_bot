package ti4.contest.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.service.CombatReplayService;
import ti4.cron.CronManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatReplaySelectionCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CombatReplaySelectionCron.class,
                CombatReplaySelectionCron::refreshSelectionSettings,
                45,
                300,
                TimeUnit.SECONDS);
    }

    private static void refreshSelectionSettings() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running CombatReplaySelectionCron.");
        try {
            SpringContext.getBean(CombatReplayService.class).refreshSelectionSnapshot();
        } catch (Exception e) {
            BotLogger.error("**CombatReplaySelectionCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatReplaySelectionCron.");
    }
}
