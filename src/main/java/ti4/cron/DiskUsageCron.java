package ti4.cron;

import java.nio.file.Path;
import java.time.ZoneId;
import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.service.storage.DiskUsageReport;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class DiskUsageCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(
                DiskUsageCron.class, DiskUsageCron::startReport, 4, 0, ZoneId.of("America/New_York"));
    }

    private static void startReport() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        Thread.ofVirtual().name("disk-usage-report").start(DiskUsageCron::logReport);
    }

    private static void logReport() {
        String storagePath = Storage.getStoragePath();
        if (storagePath == null) return;
        try {
            Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"));
            BotLogger.info(DiskUsageReport.build(Path.of(storagePath), tempDirectory));
        } catch (Exception e) {
            BotLogger.error("**DiskUsageCron failed.**", e);
        }
    }
}
