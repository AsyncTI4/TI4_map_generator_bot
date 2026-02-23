package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.statistics.StatisticsService;

@UtilityClass
public class PersistGamesToSqlCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                PersistGamesToSqlCron.class, PersistGamesToSqlCron::persist, 00, 00, ZoneId.of("America/New_York"));
    }

    private static void persist() {
        BotLogger.logCron("Running PersistGamesToSqlCron.");
        try {
            SpringContext.getBean(StatisticsService.class).persistAllGames();
        } catch (Exception e) {
            BotLogger.error("**PersistGamesToSqlCron failed.**", e);
        }
        BotLogger.logCron("Finished PersistGamesToSqlCron.");
    }
}
