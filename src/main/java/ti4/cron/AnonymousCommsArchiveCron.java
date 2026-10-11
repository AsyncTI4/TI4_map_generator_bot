package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.service.fow.AnonymousCommsService;

@UtilityClass
public class AnonymousCommsArchiveCron {

    public static void register() {
        CronManager.schedulePeriodically(
                AnonymousCommsArchiveCron.class,
                CronManager.leasedAndLogged(
                        AnonymousCommsArchiveCron.class, AnonymousCommsService::archiveQuietThreads),
                30,
                30,
                TimeUnit.MINUTES);
    }
}
