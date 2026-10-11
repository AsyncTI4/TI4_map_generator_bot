package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.service.bothelper.KeepThreadAliveService;
import ti4.service.bothelper.KeepThreadAliveService.KeptThread;

@UtilityClass
public class KeepThreadsAliveCron {

    public static void register() {
        CronManager.schedulePeriodically(
                KeepThreadsAliveCron.class,
                CronManager.leasedAndLogged(KeepThreadsAliveCron.class, KeepThreadsAliveCron::keepThreadsAlive),
                10,
                360,
                TimeUnit.MINUTES);
    }

    private static void keepThreadsAlive() {
        for (KeptThread kept : KeepThreadAliveService.getAll()) {
            try {
                KeepThreadAliveService.refreshThread(kept);
            } catch (Exception e) {
                BotLogger.error(
                        "KeepThreadsAliveCron: failed to refresh thread \"" + kept.label() + "\" (" + kept.threadId()
                                + ").",
                        e);
            }
        }
    }
}
