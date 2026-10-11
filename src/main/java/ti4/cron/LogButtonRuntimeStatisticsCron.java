package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.ButtonProcessor;
import ti4.logging.BotLogger;

@UtilityClass
public class LogButtonRuntimeStatisticsCron {

    public static void register() {
        CronManager.schedulePeriodically(
                LogButtonRuntimeStatisticsCron.class,
                CronManager.leased(() -> BotLogger.info(ButtonProcessor.getButtonProcessingStatistics())),
                1,
                4,
                TimeUnit.HOURS);
    }
}
