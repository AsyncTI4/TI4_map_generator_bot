package ti4.cron;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.service.fow.AnonymousCommsService;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class AnonymousCommsArchiveCron {

    public static void register() {
        CronManager.schedulePeriodically(
                AnonymousCommsArchiveCron.class,
                AnonymousCommsArchiveCron::archiveQuietThreads,
                30,
                30,
                TimeUnit.MINUTES);
    }

    private static void archiveQuietThreads() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running AnonymousCommsArchiveCron.");
        AnonymousCommsService.archiveQuietThreads();
        BotLogger.logCron("Finished AnonymousCommsArchiveCron.");
    }
}
