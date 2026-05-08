package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.service.persistence.SqlitePersistenceGate;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.service.persistence.PersistAllEntitiesService;

@UtilityClass
public class PersistToSqlCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                PersistToSqlCron.class, PersistToSqlCron::persist, 0, 0, ZoneId.of("America/New_York"));
    }

    private static void persist() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        if (SqlitePersistenceGate.isDisabled()) {
            BotLogger.logCron("Skipping PersistToSqlCron because SQLite-backed auxiliary persistence is disabled.");
            return;
        }
        BotLogger.logCron("Running PersistToSqlCron.");
        try {
            SpringContext.getBean(PersistAllEntitiesService.class).persistAll();
        } catch (Exception e) {
            BotLogger.error("**PersistToSqlCron failed.**", e);
        }
        BotLogger.logCron("Finished PersistToSqlCron.");
    }
}
