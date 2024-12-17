package ti4.cron;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.message.BotLogger;

@UtilityClass
public class OldUndoFileCleanupCron {

    public static void register() {
        CronManager.schedulePeriodicallyAtTime(OldUndoFileCleanupCron.class, OldUndoFileCleanupCron::cleanup, 3, 0, ZoneId.of("America/New_York"));
    }

    private static void cleanup() {
        try {
            cleanupOldUndoFiles();
        } catch (Exception e) {
            BotLogger.log("**OldUndoFileCleanupCron failed.**", e);
        }
    }

    private static void cleanupOldUndoFiles() {
        long daysOld = 60;
        Instant cutoff = Instant.now().minus(daysOld, ChronoUnit.DAYS);
        int count = 0;
        Path mapUndoDirectory = Storage.getGameUndoDirectory();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mapUndoDirectory)) {
            for (Path path : stream) {
                try {
                    FileTime lastModifiedTime = Files.getLastModifiedTime(path);
                    if (lastModifiedTime.toInstant().isBefore(cutoff)) {
                        Files.delete(path);
                        count++;
                    }
                } catch (Exception e) {
                    BotLogger.log("Failed to delete undo file: " + path.getFileName(), e);
                }
            }
        } catch (IOException e) {
            BotLogger.log("Failed to access the undo directory: " + mapUndoDirectory, e);
        }
        BotLogger.log(String.format("OldUndoFileCleanupCron: Cleaned up `%d` undo files that were over `%d` days old (%s)", count, daysOld, cutoff));
    }
}
