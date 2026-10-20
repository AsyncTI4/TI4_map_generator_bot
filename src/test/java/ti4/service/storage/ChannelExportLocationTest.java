package ti4.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChannelExportLocationTest {

    @TempDir
    Path tempDir;

    private ChannelExportLocation location;

    @BeforeEach
    void setUp() throws IOException {
        Path exportsRoot = tempDir.resolve("exported_channels");
        location = new ChannelExportLocation(exportsRoot, "123", "20261019-120000");
        Files.createDirectories(location.directory());
    }

    @Test
    void reportsNotStartedBeforeTheHostWritesAnything() {
        assertThat(location.status()).isEqualTo(ChannelExportStatus.NOT_STARTED);
    }

    @Test
    void reportsRunningOnceTheWorkDirectoryOrLogExists() throws IOException {
        Files.createDirectories(location.workDirectory());
        assertThat(location.status()).isEqualTo(ChannelExportStatus.RUNNING);

        Files.delete(location.workDirectory());
        Files.writeString(location.log(), "exporting");
        assertThat(location.status()).isEqualTo(ChannelExportStatus.RUNNING);
    }

    @Test
    void reportsDoneWhenTheDoneMarkerIsWritten() throws IOException {
        Files.writeString(location.log(), "exporting");
        Files.writeString(location.archive(), "archive");
        Files.createFile(location.doneMarker());

        assertThat(location.status()).isEqualTo(ChannelExportStatus.DONE);
    }

    @Test
    void failedMarkerWinsOverEverythingElse() throws IOException {
        Files.createDirectories(location.workDirectory());
        Files.writeString(location.failedMarker(), "Invalid format: Csv\n");

        assertThat(location.status()).isEqualTo(ChannelExportStatus.FAILED);
        assertThat(location.failureReason()).isEqualTo("Invalid format: Csv");
    }

    @Test
    void displayPathIsRelativeToTheStorageRoot() {
        assertThat(location.displayPath(location.archive())).isEqualTo("exported_channels/123/20261019-120000.tar.gz");
    }

    @Test
    void logTailKeepsOnlyTheEndOfTheLogWithoutAnsiCodes() throws IOException {
        // The exporter draws progress bars with ANSI escapes; they would be noise in a Discord message.
        String earlyLines = "early line\n".repeat(500);
        Files.writeString(location.log(), earlyLines + "\u001B[32mExport failed\u001B[0m: Forbidden\n");

        String tail = location.logTail();

        assertThat(tail).endsWith("Export failed: Forbidden");
        assertThat(tail).doesNotContain("\u001B");
        assertThat(tail.length()).isLessThanOrEqualTo(1200);
    }

    @Test
    void logTailIsEmptyWhenThereIsNoLog() throws IOException {
        assertThat(location.logTail()).isEmpty();
    }
}
