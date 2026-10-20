package ti4.discord.interactions.commands.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.RepositoryDispatchEvent;
import ti4.helpers.Storage;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.storage.ChannelExportFormat;
import ti4.service.storage.ChannelExportLocation;
import ti4.service.storage.ChannelExportRequest;
import ti4.service.storage.ChannelExportWatcher;

class StartChannelExport extends Subcommand {

    private static final int MAX_CHANNELS_PER_EXPORT = 1000;

    StartChannelExport() {
        super(Constants.START_CHANNEL_EXPORT, "Export a channel's history to exported_channels on the bot server");
        addOptions(
                new OptionData(OptionType.CHANNEL, Constants.CHANNEL, "Channel to export (defaults to current channel)")
                        .setRequired(false),
                new OptionData(
                                OptionType.BOOLEAN,
                                Constants.INCLUDE_THREADS,
                                "Also export the channel's threads, archived ones included (default true)")
                        .setRequired(false),
                new OptionData(OptionType.STRING, Constants.EXPORT_FORMAT, "Output format (default json)")
                        .setRequired(false)
                        .addChoice("json", ChannelExportFormat.JSON.getOptionValue())
                        .addChoice("html", ChannelExportFormat.HTML.getOptionValue()),
                new OptionData(OptionType.STRING, Constants.EXPORT_AFTER, "Only messages after this date (YYYY-MM-DD)")
                        .setRequired(false),
                new OptionData(
                                OptionType.STRING,
                                Constants.EXPORT_BEFORE,
                                "Only messages before this date (YYYY-MM-DD)")
                        .setRequired(false),
                new OptionData(
                                OptionType.BOOLEAN,
                                Constants.INCLUDE_MEDIA,
                                "Download attachments and images too; much larger (default false)")
                        .setRequired(false),
                new OptionData(
                                OptionType.INTEGER,
                                Constants.EXPORT_MAX_SIZE_MB,
                                "Pause once the export passes this many MB; resume to go on (default no cap)")
                        .setRequired(false)
                        .setMinValue(1));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (Storage.getStoragePath() == null) {
            MessageHelper.sendMessageToEventChannel(event, "Storage path is not configured; exports cannot be saved.");
            return;
        }

        LocalDate after;
        LocalDate before;
        try {
            after = parseDate(event.getOption(Constants.EXPORT_AFTER));
            before = parseDate(event.getOption(Constants.EXPORT_BEFORE));
        } catch (DateTimeParseException e) {
            MessageHelper.sendMessageToEventChannel(event, "Dates must look like `2026-01-31`.");
            return;
        }
        if (after != null && before != null && !after.isBefore(before)) {
            MessageHelper.sendMessageToEventChannel(event, "`after` must be earlier than `before`.");
            return;
        }

        GuildChannel channel = targetChannel(event);
        boolean includeThreads = event.getOption(Constants.INCLUDE_THREADS, true, OptionMapping::getAsBoolean);
        List<String> channelIds = collectChannelIds(channel, includeThreads);
        if (channelIds.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "There is nothing to export in <#" + channel.getId() + ">.");
            return;
        }
        boolean truncated = channelIds.size() > MAX_CHANNELS_PER_EXPORT;
        if (truncated) {
            channelIds = channelIds.subList(0, MAX_CHANNELS_PER_EXPORT);
        }

        ChannelExportRequest request = new ChannelExportRequest(
                channel.getId(),
                List.copyOf(channelIds),
                ChannelExportRequest.newExportId(Instant.now()),
                ChannelExportFormat.fromOption(event.getOption(Constants.EXPORT_FORMAT, OptionMapping::getAsString)),
                after,
                before,
                event.getOption(Constants.INCLUDE_MEDIA, false, OptionMapping::getAsBoolean),
                event.getOption(Constants.EXPORT_MAX_SIZE_MB, OptionMapping::getAsInt));

        boolean dispatched = new RepositoryDispatchEvent(
                        ChannelExportRequest.DISPATCH_EVENT_TYPE, request.toDispatchPayload())
                .sendEvent();
        if (!dispatched) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Could not start the `archive_game_channel` GitHub action. Check the bot logs.");
            return;
        }

        ChannelExportLocation location =
                new ChannelExportLocation(Storage.getChannelExportsDirectory(), channel.getId(), request.exportId());
        MessageHelper.sendMessageToEventChannel(event, startedMessage(request, location, truncated));
        ChannelExportWatcher.watch(location, event.getChannel(), ChannelExportLookup.maxUploadBytes(event));
    }

    @Nullable
    private static LocalDate parseDate(@Nullable OptionMapping option) {
        return option == null ? null : LocalDate.parse(option.getAsString().trim());
    }

    private static GuildChannel targetChannel(SlashCommandInteractionEvent event) {
        OptionMapping channelOption = event.getOption(Constants.CHANNEL);
        return channelOption != null ? channelOption.getAsChannel() : event.getGuildChannel();
    }

    private static List<String> collectChannelIds(GuildChannel channel, boolean includeThreads) {
        List<String> channelIds = new ArrayList<>();
        if (channel instanceof MessageChannel) {
            channelIds.add(channel.getId());
        }
        if (includeThreads && channel instanceof IThreadContainer threadContainer) {
            channelIds.addAll(threadIds(threadContainer));
        }
        return channelIds;
    }

    private static Set<String> threadIds(IThreadContainer threadContainer) {
        Set<String> threadIds = new LinkedHashSet<>();
        threadContainer.getThreadChannels().forEach(thread -> threadIds.add(thread.getId()));
        for (ThreadChannel thread : threadContainer.retrieveArchivedPublicThreadChannels()) {
            threadIds.add(thread.getId());
        }
        if (canListPrivateThreads(threadContainer)) {
            for (ThreadChannel thread : threadContainer.retrieveArchivedPrivateThreadChannels()) {
                threadIds.add(thread.getId());
            }
        }
        return threadIds;
    }

    private static boolean canListPrivateThreads(IThreadContainer threadContainer) {
        return threadContainer
                .getGuild()
                .getSelfMember()
                .hasPermission(threadContainer, Permission.MANAGE_THREADS, Permission.MESSAGE_HISTORY);
    }

    private static String startedMessage(
            ChannelExportRequest request, ChannelExportLocation location, boolean truncated) {
        int threadCount = request.channelIds().size() - (request.channelIds().contains(request.channelId()) ? 1 : 0);
        String message = "Started export `" + request.exportId() + "` of <#" + request.channelId() + "> with "
                + StringHelper.pluralize(threadCount, "thread") + ". It will be saved as `"
                + location.displayPath(location.archive()) + "`, and I'll post here when it finishes. Stop it with `"
                + ChannelExportWatcher.CANCEL_COMMAND + " export_id:" + request.exportId() + "`.";
        if (truncated) {
            message += "\nOnly the first " + MAX_CHANNELS_PER_EXPORT
                    + " channels and threads are included; export the remaining threads one at a time.";
        }
        return message;
    }
}
