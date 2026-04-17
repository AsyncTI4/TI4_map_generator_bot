package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionHistoryManager;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class LongExecutionHistoryCron {

    public static void register() {
        CronManager.schedulePeriodically(
                ExecutionHistoryManager.class, LongExecutionHistoryCron::logCacheStats, 0, 1, TimeUnit.MINUTES);
    }

    private static void logCacheStats() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        try {
            ExecutionHistoryManager.logLongExecutions();
        } catch (Exception e) {
            BotLogger.error("**LongExecutionHistoryCron failed.**", e);
        }
    }
}
