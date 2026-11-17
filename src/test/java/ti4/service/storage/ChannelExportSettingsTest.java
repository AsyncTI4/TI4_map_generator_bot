package ti4.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ChannelExportSettingsTest {

    // Snowflake of a channel created 2015-04-11T00:00:00Z, 100 days after the Discord epoch.
    private static final String CHANNEL_ID = "36238786560000000";
    private static final Instant CHANNEL_CREATED = Instant.parse("2015-04-11T00:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-19T12:00:00Z");

    private static String request(String after, String before, String maxSizeMb) {
        return "CHANNEL_IDS=" + CHANNEL_ID + " 111 222\n"
                + "EXPORT_FORMAT=HtmlDark\n"
                + "EXPORT_AFTER=" + after + "\n"
                + "EXPORT_BEFORE=" + before + "\n"
                + "EXPORT_MEDIA=true\n"
                + "MAX_SIZE_MB=" + maxSizeMb + "\n"
                + "END_MS=" + REQUESTED_AT.toEpochMilli() + "\n";
    }

    @Test
    void parsesTheSettingsTheExporterSaves() {
        ChannelExportSettings settings =
                ChannelExportSettings.parse(request("2015-05-01", "", "0")).orElseThrow();

        assertThat(settings.channelIds()).containsExactly(CHANNEL_ID, "111", "222");
        assertThat(settings.format()).isEqualTo(ChannelExportFormat.HTML);
        assertThat(settings.after()).isEqualTo(LocalDate.parse("2015-05-01"));
        assertThat(settings.before()).isNull();
        assertThat(settings.includeMedia()).isTrue();
        assertThat(settings.maxSizeMb()).isNull();
        assertThat(settings.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(settings.threadCount(CHANNEL_ID)).isEqualTo(2);
    }

    @Test
    void mainChannelRangeStartsAtTheAfterDateAndEndsWhenTheExportWasRequested() {
        ChannelExportSettings settings =
                ChannelExportSettings.parse(request("2015-05-01", "", "0")).orElseThrow();

        assertThat(settings.mainChannelRange(CHANNEL_ID))
                .contains(new ChannelExportSettings.DateRange(Instant.parse("2015-05-01T00:00:00Z"), REQUESTED_AT));
    }

    @Test
    void mainChannelRangeFallsBackToTheChannelCreationWithoutAnAfterDate() {
        ChannelExportSettings settings =
                ChannelExportSettings.parse(request("", "2026-01-01", "0")).orElseThrow();

        assertThat(settings.mainChannelRange(CHANNEL_ID))
                .contains(new ChannelExportSettings.DateRange(CHANNEL_CREATED, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void aBeforeDateAfterTheRequestTimeDoesNotExtendTheRange() {
        ChannelExportSettings settings =
                ChannelExportSettings.parse(request("", "2027-01-01", "0")).orElseThrow();

        assertThat(settings.mainChannelRange(CHANNEL_ID))
                .contains(new ChannelExportSettings.DateRange(CHANNEL_CREATED, REQUESTED_AT));
    }

    @Test
    void anAfterDateInTheFutureLeavesNothingToExport() {
        ChannelExportSettings settings =
                ChannelExportSettings.parse(request("2027-01-01", "", "0")).orElseThrow();

        assertThat(settings.mainChannelRange(CHANNEL_ID)).isEmpty();
    }

    @Test
    void threadsOnlyExportsHaveNoMainChannelRange() {
        ChannelExportSettings settings = ChannelExportSettings.parse(
                        "CHANNEL_IDS=111 222\nEXPORT_FORMAT=Json\nEND_MS=" + REQUESTED_AT.toEpochMilli() + "\n")
                .orElseThrow();

        assertThat(settings.includesMainChannel(CHANNEL_ID)).isFalse();
        assertThat(settings.mainChannelRange(CHANNEL_ID)).isEmpty();
    }

    @Test
    void theLastSizeCapWinsAfterAResume() {
        ChannelExportSettings settings = ChannelExportSettings.parse(request("", "", "0") + "MAX_SIZE_MB=250\n")
                .orElseThrow();

        assertThat(settings.maxSizeMb()).isEqualTo(250);
    }

    @Test
    void settingsWithoutAnEndTimeAreRejected() {
        assertThat(ChannelExportSettings.parse("CHANNEL_IDS=111\nEXPORT_FORMAT=Json\n"))
                .isEmpty();
    }
}
