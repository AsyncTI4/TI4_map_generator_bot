package ti4.discord.interactions.commands.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ti4.service.storage.ChannelExportInfo;
import ti4.service.storage.ChannelExportLocation;

class ChannelExportInfoTextTest {

    // Snowflake of a channel created 2015-04-11T00:00:00Z; 2026-10-19T12:00:00Z is epoch second 1792411200.
    private static final String CHANNEL_ID = "36238786560000000";

    @TempDir
    Path tempDir;

    private ChannelExportLocation location;

    @BeforeEach
    void setUp() throws IOException {
        Path exportsRoot = tempDir.resolve("exported_channels");
        location = new ChannelExportLocation(exportsRoot, CHANNEL_ID, "20261019-120000");
        Files.createDirectories(location.directory());
    }

    @Test
    void detailShowsTheDatesAFinishedExportCovers() throws IOException {
        Files.writeString(location.archive(), "archive");
        Files.createFile(location.doneMarker());
        Files.writeString(
                location.savedRequest(),
                "CHANNEL_IDS=" + CHANNEL_ID + " 9\nEXPORT_FORMAT=Json\nEXPORT_AFTER=2015-05-01\nEXPORT_BEFORE=\n"
                        + "EXPORT_MEDIA=false\nMAX_SIZE_MB=0\nEND_MS=1792411200000\n");

        String detail = ChannelExportInfoText.detail(ChannelExportInfo.read(location));

        assertThat(detail)
                .contains("Export `20261019-120000` of <#" + CHANNEL_ID + ">: **DONE**")
                .contains("Started: <t:1792411200:f>")
                .contains("Covers: <t:1430438400:f> to <t:1792411200:f>")
                .contains("Format: json · media: no · 1 thread · size cap: none")
                .contains("Archive: `exported_channels/" + CHANNEL_ID + "/20261019-120000.tar.gz` (7 B)")
                .doesNotContain("Resume");
    }

    @Test
    void detailSaysWhenTheSettingsWereNotKept() throws IOException {
        Files.createFile(location.doneMarker());

        String detail = ChannelExportInfoText.detail(ChannelExportInfo.read(location));

        assertThat(detail).contains("Covers: unknown").contains("Started: <t:1792411200:f>");
    }

    @Test
    void detailOfAStoppedExportExplainsHowToResumeIt() throws IOException {
        Files.writeString(location.savedRequest(), "CHANNEL_IDS=" + CHANNEL_ID + "\nEND_MS=1792411200000\n");
        Files.writeString(location.pausedMarker(), "Reached the 100 MB size cap\n");
        Files.writeString(location.partCount(), "9\n");

        String detail = ChannelExportInfoText.detail(ChannelExportInfo.read(location));

        assertThat(detail)
                .contains("**PAUSED**")
                .contains("Progress: 0 of 9 parts")
                .contains("Stopped: Reached the 100 MB size cap")
                .contains("Resume with `/admin channel_export resume export_id:20261019-120000`");
    }

    @Test
    void summaryFitsOnOneLineWithStatusSizeAndDates() throws IOException {
        Files.writeString(location.archive(), "archive");
        Files.createFile(location.doneMarker());
        Files.writeString(location.savedRequest(), "CHANNEL_IDS=" + CHANNEL_ID + "\nEND_MS=1792411200000\n");

        String summary = ChannelExportInfoText.summary(ChannelExportInfo.read(location));

        assertThat(summary)
                .isEqualTo(
                        "`20261019-120000` <#" + CHANNEL_ID + "> · DONE · 7 B · <t:1428710400:d> to <t:1792411200:d>");
    }

    @Test
    void summaryFallsBackToTheStartDateWhenTheSettingsWereNotKept() throws IOException {
        Files.createFile(location.doneMarker());

        String summary = ChannelExportInfoText.summary(ChannelExportInfo.read(location));

        assertThat(summary).endsWith("started <t:1792411200:d>");
    }
}
