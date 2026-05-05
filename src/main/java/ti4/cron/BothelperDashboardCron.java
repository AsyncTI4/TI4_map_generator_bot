package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.service.bothelper.BothelperDashboardService;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class BothelperDashboardCron {

    public static void register() {
        CronManager.scheduleOnce(BothelperDashboardCron.class, BothelperDashboardCron::run, 30, TimeUnit.SECONDS);
    }

    private static void run() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running BothelperDashboardCron.");
        BothelperDashboardService.refreshDashboardMessage();
        BotLogger.logCron("Finished BothelperDashboardCron.");
    }
}
