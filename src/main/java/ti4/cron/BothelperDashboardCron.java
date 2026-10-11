package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.service.bothelper.BothelperDashboardService;

@UtilityClass
public class BothelperDashboardCron {

    public static void register() {
        CronManager.scheduleOnce(
                BothelperDashboardCron.class,
                CronManager.leasedAndLogged(
                        BothelperDashboardCron.class, BothelperDashboardService::refreshDashboardMessage),
                30,
                TimeUnit.SECONDS);
    }
}
