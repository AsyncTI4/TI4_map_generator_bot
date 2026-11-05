package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.game.persistence.DatabaseWarmupRollout;
import ti4.spring.service.deploy.ActiveLeaseService;

@Deprecated(forRemoval = true)
@UtilityClass
public class DatabaseWarmupRolloutCron {

    private static final int INTERVAL_MINUTES = 2;

    public static void register() {
        CronManager.schedulePeriodically(
                DatabaseWarmupRolloutCron.class,
                DatabaseWarmupRolloutCron::advance,
                INTERVAL_MINUTES,
                INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    private static void advance() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        DatabaseWarmupRollout.advance();
    }
}
