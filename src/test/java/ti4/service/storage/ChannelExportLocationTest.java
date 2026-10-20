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

    private Path exportsRoot;
    private ChannelExportLocation location;

    @BeforeEach
    void setUp() throws IOException {
        exportsRoot = tempDir.resolve("exported_channels");
        location = new ChannelExportLocation(exportsRoot, "123", "20261019-120000");
        Files.createDirectories(location.directory());
    }

    @Test
    void reportsNotStartedBeforeTheHostWritesAnything() {
        assertThat(location.status()).isEqualTo(ChannelExportStatus.NOT_STARTED);
    }

    @Test
    void reportsRunningOnlyWhileTheRunningMarkerExists() throws IOException {
        // A leftover work directory from a cancelled run must not look like a live export.
        Files.createDirectories(location.workDirectory());
        Files.writeString(location.log(), "exporting");
        assertThat(location.status()).isEqualTo(ChannelExportStatus.NOT_STARTED);

        Files.createFile(location.runningMarker());
        assertThat(location.status()).isEqualTo(ChannelExportStatus.RUNNING);
    }

    @Test
    void reportsDoneWhenTheDoneMarkerIsWritten() throws IOException {
        Files.writeString(location.archive(), "archive");
        Files.createFile(location.doneMarker());

        assertThat(location.status()).isEqualTo(ChannelExportStatus.DONE);
    }

    @Test
    void failedMarkerWinsOverEverythingElse() throws IOException {
        Files.createFile(location.runningMarker());
        Files.writeString(location.failedMarker(), "Invalid format: Csv\n");

        assertThat(location.status()).isEqualTo(ChannelExportStatus.FAILED);
        assertThat(location.stopReason(location.failedMarker())).isEqualTo("Invalid format: Csv");
    }

    @Test
    void cancelRequestIsWrittenForTheExporterToPickUp() throws IOException {
        Files.createFile(location.runningMarker());

        location.requestCancel();

        assertThat(location.isCancelRequested()).isTrue();
        assertThat(location.status()).isEqualTo(ChannelExportStatus.RUNNING);
    }

    @Test
    void cancelledExportWithSavedSettingsCanBeResumed() throws IOException {
        Files.writeString(location.savedRequest(), "CHANNEL_IDS=123\n");
        Files.writeString(location.cancelledMarker(), "Stopped after 3 of 10 parts\n");
        Files.writeString(location.partCount(), "10\n");
        Files.writeString(location.progress(), "channel:0\nchannel:1\nchannel:2\n");

        assertThat(location.status()).isEqualTo(ChannelExportStatus.CANCELLED);
        assertThat(location.canResume()).isTrue();
        assertThat(location.progressSummary()).contains("3 of 10 parts");
    }

    @Test
    void exportPausedAtItsSizeCapCanBeResumed() throws IOException {
        Files.writeString(location.savedRequest(), "MAX_SIZE_MB=100\n");
        Files.writeString(
                location.pausedMarker(), "Reached the 100 MB size cap with 104 MB exported, after 4 of 9 parts\n");

        assertThat(location.status()).isEqualTo(ChannelExportStatus.PAUSED);
        assertThat(location.canResume()).isTrue();
        assertThat(location.stopReason(location.pausedMarker())).startsWith("Reached the 100 MB size cap");

        location.clearStopMarkers();

        assertThat(location.status()).isEqualTo(ChannelExportStatus.NOT_STARTED);
    }

    @Test
    void exportThatFailedBeforeSavingSettingsCannotBeResumed() throws IOException {
        Files.writeString(location.failedMarker(), "Invalid format: Csv\n");

        assertThat(location.canResume()).isFalse();
    }

    @Test
    void clearingStopMarkersLeavesProgressInPlace() throws IOException {
        Files.writeString(location.savedRequest(), "CHANNEL_IDS=123\n");
        Files.writeString(location.progress(), "channel:0\n");
        Files.writeString(location.cancelledMarker(), "Stopped after 1 of 2 parts\n");
        location.requestCancel();

        location.clearStopMarkers();

        assertThat(location.status()).isEqualTo(ChannelExportStatus.NOT_STARTED);
        assertThat(location.isCancelRequested()).isFalse();
        assertThat(location.progress()).exists();
        assertThat(location.savedRequest()).exists();
    }

    @Test
    void progressSummaryIsEmptyBeforeThePartsAreCounted() throws IOException {
        assertThat(location.progressSummary()).isEmpty();
    }

    @Test
    void findsAnExportByIdAcrossChannelDirectories() throws IOException {
        Files.writeString(location.log(), "exporting");
        Files.createDirectories(exportsRoot.resolve("456"));

        assertThat(ChannelExportLocation.find(exportsRoot, "20261019-120000")).containsExactly(location);
        assertThat(ChannelExportLocation.find(exportsRoot, "20261019-999999")).isEmpty();
    }

    @Test
    void rejectsExportIdsThatCouldEscapeTheExportsFolder() throws IOException {
        assertThat(ChannelExportLocation.isValidExportId("../maps")).isFalse();
        assertThat(ChannelExportLocation.isValidExportId("")).isFalse();
        assertThat(ChannelExportLocation.find(exportsRoot, "../123")).isEmpty();
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
