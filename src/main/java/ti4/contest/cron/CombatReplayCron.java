package ti4.contest.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.service.CombatReplayContestLifecycleService;
import ti4.cron.CronManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatReplayCron {

    private static volatile long lastRunAtMillis;

    public static void register() {
        CronManager.schedulePeriodically(
                CombatReplayCron.class, CombatReplayCron::runReplayTick, 2, 2, TimeUnit.SECONDS);
    }

    private static void runReplayTick() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        CombatContestSettings settings = SpringContext.getBean(CombatContestSettings.class);
        if (!shouldRun(settings.getReplayExecution().getReplayIntervalSeconds())) return;
        try {
            SpringContext.getBean(CombatReplayContestLifecycleService.class).runReplayTick();
        } catch (Exception e) {
            BotLogger.error("**CombatReplayCron failed.**", e);
        }
    }

    private static boolean shouldRun(int intervalSeconds) {
        long now = System.currentTimeMillis();
        if (now - lastRunAtMillis < TimeUnit.SECONDS.toMillis(intervalSeconds)) return false;
        lastRunAtMillis = now;
        return true;
    }
}
