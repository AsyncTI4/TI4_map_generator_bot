package ti4.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskUsageReportTest {

    @TempDir
    Path tempDir;

    private Path storageRoot;

    @BeforeEach
    void setUp() throws IOException {
        storageRoot = Files.createDirectories(tempDir.resolve("storage"));
    }

    @Test
    void splitsUndoFilesOutOfTheSaveFileDirectory() throws IOException {
        writeBytes(storageRoot.resolve("maps/pbd1.txt"), 100);
        writeBytes(storageRoot.resolve("maps/pbd2.txt"), 50);
        writeBytes(storageRoot.resolve("maps/undo/pbd1/pbd1_1.txt"), 300);
        writeBytes(storageRoot.resolve("maps/undo/pbd1/pbd1_2.txt"), 300);
        writeBytes(storageRoot.resolve("exported_channels/123/20261019-120000.tar.gz"), 20);

        List<DiskUsageReport.Usage> usages = DiskUsageReport.measureStorage(storageRoot);

        // Sorted largest first, so the directories worth watching are at the top of the message.
        assertThat(usages)
                .containsExactly(
                        new DiskUsageReport.Usage("maps/undo/", 600, 2),
                        new DiskUsageReport.Usage("maps/ (save files)", 150, 2),
                        new DiskUsageReport.Usage("exported_channels/", 20, 1));
    }

    @Test
    void groupsLooseFilesInTheStorageRoot() throws IOException {
        writeBytes(storageRoot.resolve("global_settings.json"), 10);
        writeBytes(storageRoot.resolve("other.json"), 5);
        Files.createDirectories(storageRoot.resolve("pm_json"));

        assertThat(DiskUsageReport.measureStorage(storageRoot))
                .containsExactly(
                        new DiskUsageReport.Usage("(files in storage root)", 15, 2),
                        new DiskUsageReport.Usage("pm_json/", 0, 0));
    }

    @Test
    void mapsWithoutUndoIsReportedAsOneDirectory() throws IOException {
        writeBytes(storageRoot.resolve("maps/pbd1.txt"), 100);

        assertThat(DiskUsageReport.measureStorage(storageRoot))
                .containsExactly(new DiskUsageReport.Usage("maps/", 100, 1));
    }

    @Test
    void reportFitsInOneDiscordMessageAndShowsTotals() throws IOException {
        writeBytes(storageRoot.resolve("maps/pbd1.txt"), 2048);
        writeBytes(storageRoot.resolve("maps/undo/pbd1/pbd1_1.txt"), 1024);
        Path tempDirectory = Files.createDirectories(tempDir.resolve("tmp"));
        writeBytes(tempDirectory.resolve("render.png"), 512);

        String report = DiskUsageReport.build(storageRoot, tempDirectory);

        assertThat(report).startsWith("**Disk usage** of `" + storageRoot + "`\nVolume: ");
        assertThat(report).contains("maps/ (save files)", "2.0 KB", "maps/undo/", "1.0 KB", "total", "3.0 KB");
        assertThat(report).contains("temp: " + tempDirectory, "512 B");
        assertThat(report).endsWith("```");
        assertThat(report.length()).isLessThan(2000);
    }

    @Test
    void formatsByteSizesWithBinaryUnits() {
        assertThat(ByteSizes.format(0)).isEqualTo("0 B");
        assertThat(ByteSizes.format(1023)).isEqualTo("1023 B");
        assertThat(ByteSizes.format(1536)).isEqualTo("1.5 KB");
        assertThat(ByteSizes.format(5L * 1024 * 1024 * 1024)).isEqualTo("5.0 GB");
    }

    private static void writeBytes(Path file, int size) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[size]);
    }
}
