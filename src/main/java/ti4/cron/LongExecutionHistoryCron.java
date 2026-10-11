package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionHistoryManager;

@UtilityClass
public class LongExecutionHistoryCron {

    public static void register() {
        CronManager.schedulePeriodically(
                LongExecutionHistoryCron.class,
                CronManager.leased(ExecutionHistoryManager::logLongExecutions),
                0,
                1,
                TimeUnit.MINUTES);
    }
}
