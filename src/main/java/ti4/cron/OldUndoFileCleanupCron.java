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
            BotLogger.error("**OldUndoFileCleanupCron failed.**", e);
        }
    }

    private static void cleanupOldUndoFiles() {
        long daysOld = 60;
        Instant cutoff = Instant.now().minus(daysOld, ChronoUnit.DAYS);
        int count = 0;

        Path baseGameUndoDirectory = Storage.getBaseGameUndoDirectory();
        try (DirectoryStream<Path> subdirectories = Files.newDirectoryStream(baseGameUndoDirectory, Files::isDirectory)) {
            for (Path subdirectory : subdirectories) {
                count += deleteOldFilesInDirectory(subdirectory, cutoff);
            }
        } catch (IOException e) {
            BotLogger.error("Error accessing directory: " + baseGameUndoDirectory, e);
        }

        BotLogger.info(String.format("OldUndoFileCleanupCron: Cleaned up `%d` undo files that were over `%d` days old (%s)", count, daysOld, cutoff));
    }

    private int deleteOldFilesInDirectory(Path directory, Instant cutoff) {
        int count = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (deleteOldFile(file, cutoff)) {
                    count++;
                }
            }
        } catch (IOException e) {
            BotLogger.error("Error accessing directory: " + directory, e);
        }

        deleteEmptyDirectory(directory);

        return count;
    }

    private boolean deleteOldFile(Path file, Instant cutoff) {
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(file);
            if (lastModifiedTime.toInstant().isBefore(cutoff)) {
                Files.delete(file);
                return true;
            }
        } catch (Exception e) {
            BotLogger.error("Failed to delete undo file: " + file, e);
        }
        return false;
    }

    private static void deleteEmptyDirectory(Path directory) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            if (!stream.iterator().hasNext()) {
                Files.delete(directory);
            }
        } catch (IOException e) {
            BotLogger.error("Error deleting empty directory: " + directory, e);
        }
    }
}
