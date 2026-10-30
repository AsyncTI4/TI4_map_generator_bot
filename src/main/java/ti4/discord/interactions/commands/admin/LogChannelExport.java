package ti4.discord.interactions.commands.admin;

import java.io.IOException;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.storage.ChannelExportLocation;

class LogChannelExport extends Subcommand {

    LogChannelExport() {
        super(Constants.LOG_CHANNEL_EXPORT, "Attach the exporter log of a channel export");
        addOptions(ChannelExportLookup.exportIdOption(), ChannelExportLookup.channelOption());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Optional<ChannelExportLocation> found = ChannelExportLookup.findForEvent(event);
        if (found.isEmpty()) return;
        ChannelExportLocation location = found.get();

        Optional<byte[]> log;
        try {
            log = location.logForUpload(ChannelExportLookup.maxUploadBytes(event));
        } catch (IOException e) {
            BotLogger.error("Could not read the log of channel export " + location.exportId(), e);
            MessageHelper.sendMessageToEventChannel(event, "Could not read the export log. Check the bot logs.");
            return;
        }
        if (log.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Export `" + location.exportId() + "` has no log yet (" + location.status() + ").");
            return;
        }

        MessageHelper.sendMessageToEventChannel(
                event, "Log of export `" + location.exportId() + "` of <#" + location.channelId() + ">:");
        MessageHelper.sendFileUploadToChannel(
                event.getMessageChannel(), FileUpload.fromData(log.get(), location.exportId() + ".log"));
    }
}
