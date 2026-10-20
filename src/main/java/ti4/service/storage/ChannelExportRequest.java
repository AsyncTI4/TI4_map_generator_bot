package ti4.service.storage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public record ChannelExportRequest(
        String channelId,
        List<String> channelIds,
        String exportId,
        ChannelExportFormat format,
        @Nullable LocalDate after,
        @Nullable LocalDate before,
        boolean includeMedia,
        @Nullable Integer maxSizeMb) {

    public static final String DISPATCH_EVENT_TYPE = "archive_game_channel";

    private static final DateTimeFormatter EXPORT_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public static String newExportId(Instant now) {
        return EXPORT_ID_FORMAT.format(now);
    }

    public static Map<String, String> resumeDispatchPayload(
            ChannelExportLocation location, @Nullable Integer newMaxSizeMb) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("channel", location.channelId());
        payload.put("export_id", location.exportId());
        payload.put("resume", "true");
        if (newMaxSizeMb != null) {
            payload.put("max_size_mb", newMaxSizeMb.toString());
        }
        return payload;
    }

    public Map<String, String> toDispatchPayload() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("channel", channelId);
        payload.put("channels", String.join(" ", channelIds));
        payload.put("export_id", exportId);
        payload.put("format", format.getExporterName());
        payload.put("after", dateOrBlank(after));
        payload.put("before", dateOrBlank(before));
        payload.put("media", String.valueOf(includeMedia));
        payload.put("max_size_mb", maxSizeMb == null ? "" : maxSizeMb.toString());
        return payload;
    }

    private static String dateOrBlank(@Nullable LocalDate date) {
        return date == null ? "" : date.toString();
    }
}
