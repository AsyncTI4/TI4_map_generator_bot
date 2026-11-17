package ti4.discord.interactions.commands.admin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.storage.ChannelExportInfo;
import ti4.service.storage.ChannelExportLocation;

class InfoChannelExport extends Subcommand {

    private static final int MAX_LISTED = 25;

    InfoChannelExport() {
        super(Constants.INFO_CHANNEL_EXPORT, "Show channel exports on disk: id, status, size and dates covered");
        addOptions(
                new OptionData(
                                OptionType.STRING,
                                Constants.EXPORT_ID,
                                "Export id to show in detail; leave empty to list the exports on disk")
                        .setRequired(false),
                new OptionData(OptionType.CHANNEL, Constants.CHANNEL, "Only exports of this channel")
                        .setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String exportId = event.getOption(Constants.EXPORT_ID, "", OptionMapping::getAsString)
                .trim();
        if (!exportId.isEmpty()) {
            ChannelExportLookup.findForEvent(event).ifPresent(location -> sendDetail(event, location));
            return;
        }
        if (ChannelExportLookup.isStorageConfigured(event)) {
            sendList(event);
        }
    }

    private static void sendDetail(SlashCommandInteractionEvent event, ChannelExportLocation location) {
        try {
            MessageHelper.sendMessageToEventChannel(
                    event, ChannelExportInfoText.detail(ChannelExportInfo.read(location)));
        } catch (IOException e) {
            BotLogger.error("Could not read channel export " + location.exportId(), e);
            MessageHelper.sendMessageToEventChannel(event, "Could not read the export's files. Check the bot logs.");
        }
    }

    private static void sendList(SlashCommandInteractionEvent event) {
        List<ChannelExportLocation> locations;
        try {
            locations = exportsMatching(event);
        } catch (IOException e) {
            BotLogger.error("Could not list channel exports", e);
            MessageHelper.sendMessageToEventChannel(event, "Could not read the exported_channels folder.");
            return;
        }
        if (locations.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No channel exports were found on disk.");
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Channel exports on disk, newest first (" + locations.size() + " total):");
        try {
            for (ChannelExportLocation location :
                    locations.stream().limit(MAX_LISTED).toList()) {
                lines.add(ChannelExportInfoText.summary(ChannelExportInfo.read(location)));
            }
        } catch (IOException e) {
            BotLogger.error("Could not read channel export files", e);
            MessageHelper.sendMessageToEventChannel(event, "Could not read the export's files. Check the bot logs.");
            return;
        }
        if (locations.size() > MAX_LISTED) {
            lines.add("...and " + (locations.size() - MAX_LISTED) + " older. Narrow it with the `channel` option.");
        }
        lines.add("Pass `export_id` for details.");
        MessageHelper.sendMessageToEventChannel(event, String.join("\n", lines));
    }

    private static List<ChannelExportLocation> exportsMatching(SlashCommandInteractionEvent event) throws IOException {
        List<ChannelExportLocation> all = ChannelExportLocation.findAll(Storage.getChannelExportsDirectory());
        OptionMapping channelOption = event.getOption(Constants.CHANNEL);
        if (channelOption == null) return all;
        String channelId = channelOption.getAsChannel().getId();
        return all.stream()
                .filter(location -> location.channelId().equals(channelId))
                .toList();
    }
}
