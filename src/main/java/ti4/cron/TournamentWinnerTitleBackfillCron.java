package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.service.tournamentwinner.TourneyWinnerService;

@UtilityClass
public class TournamentWinnerTitleBackfillCron {

    public static void register() {
        CronManager.scheduleOnceAfterLeaseAcquired(
                TournamentWinnerTitleBackfillCron.class,
                TournamentWinnerTitleBackfillCron::grantMissingTitles,
                0,
                TimeUnit.SECONDS);
    }

    private static void grantMissingTitles() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        try {
            int granted = TourneyWinnerService.getBean().grantMissingWinnerTitles();
            BotLogger.info("Tournament winner title backfill granted " + granted + " title(s).");
        } catch (Exception e) {
            BotLogger.error("**TournamentWinnerTitleBackfillCron failed.**", e);
        }
    }
}
