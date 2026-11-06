package ti4.discord.interactions.commands.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.TimeFormat;
import ti4.helpers.StringHelper;
import ti4.service.storage.ByteSizes;
import ti4.service.storage.ChannelExportInfo;
import ti4.service.storage.ChannelExportLocation;
import ti4.service.storage.ChannelExportSettings;
import ti4.service.storage.ChannelExportWatcher;

@UtilityClass
class ChannelExportInfoText {

    static String detail(ChannelExportInfo info) {
        ChannelExportLocation location = info.location();
        List<String> lines = new ArrayList<>();
        lines.add("Export `" + location.exportId() + "` of <#" + location.channelId() + ">: **" + info.status() + "**");
        info.startedAt().ifPresent(startedAt -> lines.add("Started: " + dateTime(startedAt)));
        lines.add(coverage(info));
        info.settings().ifPresent(settings -> lines.add(settingsLine(location, settings)));
        info.progress().ifPresent(progress -> lines.add("Progress: " + progress));
        info.stopReason().ifPresent(reason -> lines.add("Stopped: " + reason));
        info.archiveBytes()
                .ifPresent(bytes -> lines.add("Archive: `" + location.displayPath(location.archive()) + "` ("
                        + ByteSizes.format(bytes) + ")"));
        info.logUpdatedAt()
                .ifPresent(updatedAt -> lines.add("Log last written: " + TimeFormat.RELATIVE.atInstant(updatedAt)));
        if (location.canResume()) {
            lines.add(
                    "Resume with `" + ChannelExportWatcher.RESUME_COMMAND + " export_id:" + location.exportId() + "`.");
        }
        return String.join("\n", lines);
    }

    static String summary(ChannelExportInfo info) {
        ChannelExportLocation location = info.location();
        List<String> parts = new ArrayList<>();
        parts.add("`" + location.exportId() + "` <#" + location.channelId() + ">");
        parts.add(info.status().toString());
        info.archiveBytes().ifPresent(bytes -> parts.add(ByteSizes.format(bytes)));
        parts.add(shortCoverage(info));
        return String.join(" · ", parts);
    }

    private static String coverage(ChannelExportInfo info) {
        ChannelExportLocation location = info.location();
        if (info.settings().isEmpty()) {
            return "Covers: unknown, the export's settings were not kept";
        }
        ChannelExportSettings settings = info.settings().get();
        if (!settings.includesMainChannel(location.channelId())) {
            return "Covers: threads only";
        }
        return settings.mainChannelRange(location.channelId())
                .map(range -> "Covers: " + dateTime(range.from()) + " to " + dateTime(range.to()))
                .orElse("Covers: no messages of the main channel");
    }

    private static String shortCoverage(ChannelExportInfo info) {
        ChannelExportLocation location = info.location();
        return info.settings()
                .flatMap(settings -> settings.mainChannelRange(location.channelId()))
                .map(range -> TimeFormat.DATE_SHORT.atInstant(range.from()) + " to "
                        + TimeFormat.DATE_SHORT.atInstant(range.to()))
                .or(() -> info.startedAt().map(startedAt -> "started " + TimeFormat.DATE_SHORT.atInstant(startedAt)))
                .orElse("dates unknown");
    }

    private static String settingsLine(ChannelExportLocation location, ChannelExportSettings settings) {
        String sizeCap = settings.maxSizeMb() == null ? "none" : settings.maxSizeMb() + " MB";
        return "Format: " + settings.format().getOptionValue() + " · media: "
                + (settings.includeMedia() ? "yes" : "no") + " · "
                + StringHelper.pluralize(settings.threadCount(location.channelId()), "thread") + " · size cap: "
                + sizeCap;
    }

    private static String dateTime(Instant instant) {
        return TimeFormat.DATE_TIME_SHORT.atInstant(instant).toString();
    }
}
