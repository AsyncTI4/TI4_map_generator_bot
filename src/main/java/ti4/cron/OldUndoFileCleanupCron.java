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

        Path baseGameUndoDirectory = Storage.getBaseGameUndoDirectory();
        try (DirectoryStream<Path> subdirectories = Files.newDirectoryStream(baseGameUndoDirectory, Files::isDirectory)) {
            for (Path subdirectory : subdirectories) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(subdirectory)) {
                    for (Path file : files) {
                        try {
                            FileTime lastModifiedTime = Files.getLastModifiedTime(file);
                            if (lastModifiedTime.toInstant().isBefore(cutoff)) {
                                Files.delete(file);
                                count++;
                            }
                        } catch (Exception e) {
                            BotLogger.log("Failed to delete undo file: " + file, e);
                        }
                    }
                } catch (IOException e) {
                    BotLogger.log("Error accessing directory: " + subdirectory, e);
                }
            }
        } catch (IOException e) {
            BotLogger.log("Error accessing directory: " + baseGameUndoDirectory, e);
        }

        BotLogger.log(String.format("OldUndoFileCleanupCron: Cleaned up `%d` undo files that were over `%d` days old (%s)", count, daysOld, cutoff));
    }
}
