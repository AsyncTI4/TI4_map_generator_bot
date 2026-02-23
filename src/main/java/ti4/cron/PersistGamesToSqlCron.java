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
                PersistGamesToSqlCron.class, PersistGamesToSqlCron::persistPayloads, 13, 15, ZoneId.of("UTC"));
    }

    private static void persistPayloads() {
        BotLogger.logCron("Running PersistGameStatsDashboardPayloadsCron.");
        try {
            SpringContext.getBean(StatisticsService.class).persistAllGames();
        } catch (Exception e) {
            BotLogger.error("**PersistGameStatsDashboardPayloadsCron failed.**", e);
        }
        BotLogger.logCron("Finished PersistGameStatsDashboardPayloadsCron.");
    }
}
