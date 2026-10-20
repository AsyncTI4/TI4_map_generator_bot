package ti4.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelExportRequestTest {

    @Test
    void payloadMatchesWhatTheArchiveWorkflowReads() {
        var request = new ChannelExportRequest(
                "111",
                List.of("111", "222", "333"),
                "20261019-120000",
                ChannelExportFormat.HTML,
                LocalDate.of(2026, 1, 1),
                null,
                true,
                100);

        assertThat(request.toDispatchPayload())
                .containsExactly(
                        Map.entry("channel", "111"),
                        Map.entry("channels", "111 222 333"),
                        Map.entry("export_id", "20261019-120000"),
                        Map.entry("format", "HtmlDark"),
                        Map.entry("after", "2026-01-01"),
                        Map.entry("before", ""),
                        Map.entry("media", "true"),
                        Map.entry("max_size_mb", "100"));
    }

    @Test
    void payloadStaysUnderGitHubsTenPropertyLimit() {
        // RespositoryDispatchClientPayload silently drops the whole payload at 10 or more properties.
        var request = new ChannelExportRequest(
                "111", List.of("111"), "id", ChannelExportFormat.JSON, null, null, false, null);

        assertThat(request.toDispatchPayload()).hasSizeLessThan(10);
    }

    @Test
    void resumePayloadOnlyNamesTheExportBecauseTheHostKeepsTheSettings() {
        var location = new ChannelExportLocation(Path.of("exported_channels"), "111", "20261019-120000");

        assertThat(ChannelExportRequest.resumeDispatchPayload(location, null))
                .containsExactly(
                        Map.entry("channel", "111"),
                        Map.entry("export_id", "20261019-120000"),
                        Map.entry("resume", "true"));
    }

    @Test
    void resumePayloadCarriesANewSizeCapWhenOneIsGiven() {
        // 0 is meaningful: it tells the host to remove the cap for the rest of the export.
        var location = new ChannelExportLocation(Path.of("exported_channels"), "111", "20261019-120000");

        assertThat(ChannelExportRequest.resumeDispatchPayload(location, 0)).containsEntry("max_size_mb", "0");
    }

    @Test
    void blankSizeCapMeansNoCap() {
        var request = new ChannelExportRequest(
                "111", List.of("111"), "id", ChannelExportFormat.JSON, null, null, false, null);

        assertThat(request.toDispatchPayload()).containsEntry("max_size_mb", "");
    }

    @Test
    void exportIdIsAUtcTimestampSafeForFileNames() {
        assertThat(ChannelExportRequest.newExportId(Instant.parse("2026-10-19T04:05:06Z")))
                .isEqualTo("20261019-040506");
    }

    @Test
    void formatFallsBackToJson() {
        assertThat(ChannelExportFormat.fromOption(null)).isEqualTo(ChannelExportFormat.JSON);
        assertThat(ChannelExportFormat.fromOption("csv")).isEqualTo(ChannelExportFormat.JSON);
        assertThat(ChannelExportFormat.fromOption("HTML")).isEqualTo(ChannelExportFormat.HTML);
    }
}
