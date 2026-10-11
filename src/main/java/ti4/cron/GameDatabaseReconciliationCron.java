package ti4.cron;

import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.service.persistence.GameDatabaseReconciler;

@UtilityClass
public class GameDatabaseReconciliationCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                GameDatabaseReconciliationCron.class,
                CronManager.leased(GameDatabaseReconciliationCron::reconcile),
                0,
                0,
                ZoneId.of("America/New_York"));
    }

    private static void reconcile() {
        if (DatabasePersistenceGate.isDisabled()) {
            BotLogger.logCron("Skipping GameDatabaseReconciliationCron because database maintenance mode is active.");
            return;
        }
        BotLogger.logCron("Queueing GameDatabaseReconciliationCron.");
        GameDatabaseReconciler.queueReconciliation();
    }
}
