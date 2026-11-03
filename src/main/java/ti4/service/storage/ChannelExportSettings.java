package ti4.service.storage;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.utils.TimeUtil;

public record ChannelExportSettings(
        List<String> channelIds,
        ChannelExportFormat format,
        @Nullable LocalDate after,
        @Nullable LocalDate before,
        boolean includeMedia,
        @Nullable Integer maxSizeMb,
        Instant requestedAt) {

    public record DateRange(Instant from, Instant to) {}

    public static Optional<ChannelExportSettings> parse(String requestFile) {
        Map<String, String> values = new HashMap<>();
        requestFile.lines().forEach(line -> {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(
                        line.substring(0, separator),
                        line.substring(separator + 1).trim());
            }
        });
        try {
            return Optional.of(new ChannelExportSettings(
                    Arrays.stream(values.getOrDefault("CHANNEL_IDS", "").split("\\s+"))
                            .filter(id -> !id.isBlank())
                            .toList(),
                    ChannelExportFormat.fromExporterName(values.get("EXPORT_FORMAT")),
                    parseDate(values.get("EXPORT_AFTER")),
                    parseDate(values.get("EXPORT_BEFORE")),
                    Boolean.parseBoolean(values.get("EXPORT_MEDIA")),
                    parseSizeCap(values.get("MAX_SIZE_MB")),
                    Instant.ofEpochMilli(Long.parseLong(values.getOrDefault("END_MS", "")))));
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean includesMainChannel(String channelId) {
        return channelIds.contains(channelId);
    }

    public int threadCount(String channelId) {
        return channelIds.size() - (includesMainChannel(channelId) ? 1 : 0);
    }

    public Optional<DateRange> mainChannelRange(String channelId) {
        if (!includesMainChannel(channelId)) return Optional.empty();
        Instant from = createdAt(channelId);
        Instant to = requestedAt;
        if (from == null) return Optional.empty();
        if (after != null && startOfDay(after).isAfter(from)) {
            from = startOfDay(after);
        }
        if (before != null && startOfDay(before).isBefore(to)) {
            to = startOfDay(before);
        }
        return to.isAfter(from) ? Optional.of(new DateRange(from, to)) : Optional.empty();
    }

    @Nullable
    private static Instant createdAt(String channelId) {
        try {
            return TimeUtil.getTimeCreated(Long.parseLong(channelId)).toInstant();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Nullable
    private static LocalDate parseDate(@Nullable String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    @Nullable
    private static Integer parseSizeCap(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        int megabytes = Integer.parseInt(value);
        return megabytes > 0 ? megabytes : null;
    }
}
