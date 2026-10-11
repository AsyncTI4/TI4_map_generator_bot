package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.service.statistics.game.WinningPathPersistenceService;

@UtilityClass
public class WinningPathCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                WinningPathCron.class,
                CronManager.leasedAndLogged(WinningPathCron.class, WinningPathPersistenceService::recomputeFile),
                4,
                0,
                ZoneId.of("America/New_York"));
    }
}
