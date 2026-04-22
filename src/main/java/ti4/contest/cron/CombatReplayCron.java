package ti4.contest.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.service.CombatReplayContestLifecycleService;
import ti4.cron.CronManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatReplayCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CombatReplayCron.class, CombatReplayCron::runReplayTick, 30, 15, TimeUnit.SECONDS);
    }

    private static void runReplayTick() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        try {
            SpringContext.getBean(CombatReplayContestLifecycleService.class).runReplayTick();
        } catch (Exception e) {
            BotLogger.error("**CombatReplayCron failed.**", e);
        }
    }
}
