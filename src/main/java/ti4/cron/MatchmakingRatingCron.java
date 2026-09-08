package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingCalculationService;

@UtilityClass
public class MatchmakingRatingCron {

    private static final int FIRST_RUN_HOUR = 1;
    private static final int FIRST_RUN_MINUTE = 0;
    private static final int PERIOD_HOURS = 8;

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                MatchmakingRatingCron.class,
                MatchmakingRatingCron::recalculate,
                FIRST_RUN_HOUR,
                FIRST_RUN_MINUTE,
                ZoneId.of("America/New_York"),
                PERIOD_HOURS);
    }

    private static void recalculate() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        if (DatabasePersistenceGate.isDisabled()) {
            BotLogger.logCron("Skipping MatchmakingRatingCron because database maintenance mode is active.");
            return;
        }
        BotLogger.logCron("Running MatchmakingRatingCron.");
        try {
            SpringContext.getBean(MatchmakingRatingCalculationService.class).recalculateAndStore();
        } catch (Exception e) {
            BotLogger.error("**MatchmakingRatingCron failed.**", e);
        }
        BotLogger.logCron("Finished MatchmakingRatingCron.");
    }
}
