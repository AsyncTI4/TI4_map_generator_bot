package ti4.cron;

import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.message.BotLogger;
import ti4.processors.ButtonProcessor;

@UtilityClass
public class LogButtonRuntimeStatisticsCron {

    public static void register() {
        CronManager.schedulePeriodically(LogButtonRuntimeStatisticsCron.class, LogButtonRuntimeStatisticsCron::logButtonRuntimeStatistics, 1, 4, TimeUnit.HOURS);
    }

    private static void logButtonRuntimeStatistics() {
        try {
            String buttonProcessingStatistics = ButtonProcessor.getButtonProcessingStatistics();
            BotLogger.info(buttonProcessingStatistics);
        } catch (Exception e) {
            BotLogger.error("**LogButtonRuntimeStatisticsCron failed.**", e);
        }
    }
}
