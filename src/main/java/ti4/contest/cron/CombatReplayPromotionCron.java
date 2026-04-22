package ti4.contest.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.service.CombatReplayContestLifecycleService;
import ti4.cron.CronManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatReplayPromotionCron {

    public static void register() {
        CronManager.schedulePeriodically(
                CombatReplayPromotionCron.class,
                CombatReplayPromotionCron::promoteBestCandidate,
                20,
                60,
                TimeUnit.SECONDS);
    }

    private static void promoteBestCandidate() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running CombatReplayPromotionCron.");
        try {
            SpringContext.getBean(CombatReplayContestLifecycleService.class).promoteBestCandidateIfDue();
        } catch (Exception e) {
            BotLogger.error("**CombatReplayPromotionCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatReplayPromotionCron.");
    }
}
