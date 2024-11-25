package ti4.cron;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.time.StopWatch;
import ti4.helpers.GlobalSettings;
import ti4.helpers.WebHelper;
import ti4.map.PersistenceManager;
import ti4.message.BotLogger;

@UtilityClass
public class UploadStatsCron {

    private static final String JSON_DATA_FILE_NAME = "UploadStatsCronData.json";
    private static final int UPLOAD_STATS_INTERVAL_DAYS = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.UPLOAD_STATS_INTERVAL_DAYS.toString(), Integer.class, 7);
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public static void register() {
        CronManager.register(UploadStatsCron::uploadStats, 0, 0, ZoneId.of("America/New_York"));
    }

    private static void uploadStats() {
        var stopWatch = StopWatch.createStarted();

        var uploadStatsCronData = readUploadStatsCronData();
        if (uploadStatsCronData != null && uploadStatsCronData.lastUpload != null &&
                uploadStatsCronData.lastUpload.plusDays(UPLOAD_STATS_INTERVAL_DAYS).isBefore(LocalDate.now())) {
            return;
        }

        BotLogger.log("Starting stats upload.");
        WebHelper.putStats();
        persistUploadStatsCronData();
        BotLogger.log("Finished stats upload in: " + stopWatch.getDuration());
    }

    private static UploadStatsCronData readUploadStatsCronData() {
        try {
            return PersistenceManager.readObjectFromJsonFile(JSON_DATA_FILE_NAME, UploadStatsCronData.class);
        } catch (IOException e) {
            BotLogger.log("Failed to read json data for UploadStatsCron. Stats will be uploaded more often than intended.", e);
            PersistenceManager.deleteJsonFile(JSON_DATA_FILE_NAME);
            return null;
        }
    }

    private static void persistUploadStatsCronData() {
        try {
            PersistenceManager.writeObjectToJsonFile(JSON_DATA_FILE_NAME, new UploadStatsCronData(LocalDate.now()));
        } catch (Exception e) {
            BotLogger.log("Failed to write json data for UploadStatsCron. Stats will be uploaded more often than intended.", e);
        }
    }

    private record UploadStatsCronData(@JsonFormat(pattern = "yyyy-MM-dd") LocalDate lastUpload) {}
}
