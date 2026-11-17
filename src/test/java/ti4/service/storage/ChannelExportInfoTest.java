package ti4.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChannelExportInfoTest {

    @TempDir
    Path tempDir;

    private Path exportsRoot;

    @BeforeEach
    void setUp() throws IOException {
        exportsRoot = tempDir.resolve("exported_channels");
        Files.createDirectories(exportsRoot.resolve("123"));
        Files.createDirectories(exportsRoot.resolve("456"));
    }

    private ChannelExportLocation location(String channelId, String exportId) {
        return new ChannelExportLocation(exportsRoot, channelId, exportId);
    }

    @Test
    void readsAFinishedExportWithItsKeptSettings() throws IOException {
        ChannelExportLocation location = location("123", "20261019-120000");
        Files.writeString(location.archive(), "archive");
        Files.createFile(location.doneMarker());
        Files.writeString(
                location.savedRequest(),
                "CHANNEL_IDS=123 9\nEXPORT_FORMAT=Json\nEXPORT_AFTER=\nEXPORT_BEFORE=\nEXPORT_MEDIA=false\n"
                        + "MAX_SIZE_MB=0\nEND_MS=1792411200000\n");

        ChannelExportInfo info = ChannelExportInfo.read(location);

        assertThat(info.status()).isEqualTo(ChannelExportStatus.DONE);
        assertThat(info.startedAt()).contains(Instant.parse("2026-10-19T12:00:00Z"));
        assertThat(info.settings()).isPresent();
        assertThat(info.archiveBytes()).hasValue(7);
        assertThat(info.stopReason()).isEmpty();
    }

    @Test
    void readsAFinishedExportWhoseSettingsWereNotKept() throws IOException {
        ChannelExportLocation location = location("123", "20261019-120000");
        Files.createFile(location.doneMarker());

        ChannelExportInfo info = ChannelExportInfo.read(location);

        assertThat(info.settings()).isEmpty();
        assertThat(info.startedAt()).contains(Instant.parse("2026-10-19T12:00:00Z"));
    }

    @Test
    void readsWhyAnExportStoppedAndHowFarItGot() throws IOException {
        ChannelExportLocation location = location("123", "20261019-120000");
        Files.writeString(location.savedRequest(), "CHANNEL_IDS=123\n");
        Files.writeString(location.cancelledMarker(), "Stopped after 3 of 10 parts\n");
        Files.writeString(location.partCount(), "10\n");
        Files.writeString(location.progress(), "channel:0\nchannel:1\nchannel:2\n");

        ChannelExportInfo info = ChannelExportInfo.read(location);

        assertThat(info.status()).isEqualTo(ChannelExportStatus.CANCELLED);
        assertThat(info.stopReason()).contains("Stopped after 3 of 10 parts");
        assertThat(info.progress()).contains("3 of 10 parts");
        assertThat(info.settings()).isEmpty();
    }

    @Test
    void exportIdsThatAreNotTimestampsHaveNoStartTime() throws IOException {
        ChannelExportLocation location = location("123", "manual-backup");
        Files.writeString(location.log(), "exporting");

        assertThat(ChannelExportInfo.read(location).startedAt()).isEmpty();
    }

    @Test
    void listsEveryExportOnDiskNewestFirstAcrossChannels() throws IOException {
        ChannelExportLocation older = location("123", "20261019-120000");
        ChannelExportLocation newerOtherChannel = location("456", "20261101-080000");
        Files.createFile(older.doneMarker());
        Files.writeString(older.archive(), "archive");
        Files.writeString(newerOtherChannel.log(), "exporting");
        Files.createDirectories(older.workDirectory());
        Files.writeString(exportsRoot.resolve("123").resolve("20261019-120000.sh"), "script");

        assertThat(ChannelExportLocation.findAll(exportsRoot)).containsExactly(newerOtherChannel, older);
    }

    @Test
    void listsNothingWhenTheExportsFolderDoesNotExist() throws IOException {
        assertThat(ChannelExportLocation.findAll(tempDir.resolve("missing"))).isEmpty();
    }
}
