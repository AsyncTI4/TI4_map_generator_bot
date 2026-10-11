package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.message.GameMessageManager;

@UtilityClass
public class GameMessageCleanupCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                GameMessageCleanupCron.class,
                CronManager.leasedAndLogged(GameMessageCleanupCron.class, GameMessageManager::cleanupStaleEntries),
                4,
                0,
                ZoneId.of("America/New_York"));
    }
}
