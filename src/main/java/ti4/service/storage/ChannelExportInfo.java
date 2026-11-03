package ti4.service.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

public record ChannelExportInfo(
        ChannelExportLocation location,
        ChannelExportStatus status,
        Optional<Instant> startedAt,
        Optional<ChannelExportSettings> settings,
        Optional<String> progress,
        Optional<String> stopReason,
        OptionalLong archiveBytes,
        Optional<Instant> logUpdatedAt) {

    public static ChannelExportInfo read(ChannelExportLocation location) throws IOException {
        ChannelExportStatus status = location.status();
        return new ChannelExportInfo(
                location,
                status,
                ChannelExportRequest.parseExportId(location.exportId()),
                readSettings(location),
                location.progressSummary(),
                readStopReason(location, status),
                readArchiveBytes(location),
                readLogUpdatedAt(location));
    }

    private static Optional<ChannelExportSettings> readSettings(ChannelExportLocation location) throws IOException {
        if (!Files.exists(location.savedRequest())) return Optional.empty();
        return ChannelExportSettings.parse(Files.readString(location.savedRequest(), StandardCharsets.UTF_8));
    }

    private static Optional<String> readStopReason(ChannelExportLocation location, ChannelExportStatus status)
            throws IOException {
        Path marker =
                switch (status) {
                    case FAILED -> location.failedMarker();
                    case CANCELLED -> location.cancelledMarker();
                    case PAUSED -> location.pausedMarker();
                    case NOT_STARTED, RUNNING, DONE -> null;
                };
        return marker == null ? Optional.empty() : Optional.of(location.stopReason(marker));
    }

    private static OptionalLong readArchiveBytes(ChannelExportLocation location) throws IOException {
        return Files.exists(location.archive())
                ? OptionalLong.of(Files.size(location.archive()))
                : OptionalLong.empty();
    }

    private static Optional<Instant> readLogUpdatedAt(ChannelExportLocation location) throws IOException {
        if (!Files.exists(location.log())) return Optional.empty();
        return Optional.of(Files.getLastModifiedTime(location.log()).toInstant());
    }
}
