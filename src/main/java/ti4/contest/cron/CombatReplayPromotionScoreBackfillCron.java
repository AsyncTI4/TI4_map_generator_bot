package ti4.contest.cron;

import lombok.experimental.UtilityClass;
import ti4.contest.replay.service.CombatReplayPromotionScoreBackfillService;
import ti4.cron.CronManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatReplayPromotionScoreBackfillCron {

    public static void register() {
        CronManager.registerManual(
                CombatReplayPromotionScoreBackfillCron.class, CombatReplayPromotionScoreBackfillCron::runBackfill);
    }

    private static void runBackfill() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running CombatReplayPromotionScoreBackfillCron.");
        try {
            SpringContext.getBean(CombatReplayPromotionScoreBackfillService.class)
                    .runBackfill();
        } catch (Exception e) {
            BotLogger.error("**CombatReplayPromotionScoreBackfillCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatReplayPromotionScoreBackfillCron.");
    }
}
