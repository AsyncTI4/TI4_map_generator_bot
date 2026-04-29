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
public class CombatReplayPromotionCron {

    private static volatile long lastRunAtMillis;

    public static void register() {
        CronManager.schedulePeriodically(
                CombatReplayPromotionCron.class,
                CombatReplayPromotionCron::promoteBestCandidate,
                5,
                5,
                TimeUnit.SECONDS);
    }

    private static void promoteBestCandidate() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        CombatContestSettings settings = SpringContext.getBean(CombatContestSettings.class);
        if (!settings.getRuntime().isImmediatePromotionOnResolve()
                && !shouldRun(settings.getPromotion().getIntervalSeconds())) return;
        BotLogger.logCron("Running CombatReplayPromotionCron.");
        try {
            SpringContext.getBean(CombatReplayContestLifecycleService.class).promoteBestCandidateIfDue();
        } catch (Exception e) {
            BotLogger.error("**CombatReplayPromotionCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatReplayPromotionCron.");
    }

    private static boolean shouldRun(int intervalSeconds) {
        long now = System.currentTimeMillis();
        if (now - lastRunAtMillis < TimeUnit.SECONDS.toMillis(intervalSeconds)) return false;
        lastRunAtMillis = now;
        return true;
    }
}
