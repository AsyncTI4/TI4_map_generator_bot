package ti4.contest.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.service.CombatReplayService;
import ti4.cron.CronManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatReplaySelectionCron {

    private static volatile long lastRunAtMillis;

    public static void register() {
        CronManager.schedulePeriodically(
                CombatReplaySelectionCron.class,
                CombatReplaySelectionCron::refreshSelectionSettings,
                5,
                5,
                TimeUnit.SECONDS);
    }

    private static void refreshSelectionSettings() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        CombatContestSettings settings = SpringContext.getBean(CombatContestSettings.class);
        if (!shouldRun(settings.getCandidateSelection().getWindow().getRefreshCronIntervalSeconds())) return;
        BotLogger.logCron("Running CombatReplaySelectionCron.");
        try {
            SpringContext.getBean(CombatReplayService.class).refreshSelectionSnapshot();
        } catch (Exception e) {
            BotLogger.error("**CombatReplaySelectionCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatReplaySelectionCron.");
    }

    private static boolean shouldRun(int intervalSeconds) {
        long now = System.currentTimeMillis();
        if (now - lastRunAtMillis < TimeUnit.SECONDS.toMillis(intervalSeconds)) return false;
        lastRunAtMillis = now;
        return true;
    }
}
