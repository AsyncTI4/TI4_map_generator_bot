package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.PersistAllEntitiesService;

@UtilityClass
public class PersistToSqlCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                PersistToSqlCron.class, PersistToSqlCron::persist, 00, 00, ZoneId.of("America/New_York"));
    }

    private static void persist() {
        BotLogger.logCron("Running PersistToSqlCron.");
        try {
            SpringContext.getBean(PersistAllEntitiesService.class).persistAll();
        } catch (Exception e) {
            BotLogger.error("**PersistToSqlCron failed.**", e);
        }
        BotLogger.logCron("Finished PersistToSqlCron.");
    }
}
