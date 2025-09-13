package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.winningpath.WinningPathPersistenceService;

@UtilityClass
public class WinningPathCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                WinningPathCron.class, WinningPathCron::recompute, 4, 0, ZoneId.of("America/New_York"));
    }

    private static void recompute() {
        BotLogger.logCron("Running WinningPathCron.");
        try {
            SpringContext.getBean(WinningPathPersistenceService.class).recompute();
        } catch (Exception e) {
            BotLogger.error("**WinningPathCron failed.**", e);
        }
        BotLogger.logCron("Finished WinningPathCron.");
    }
}
