package ti4.discord.interactions.commands.admin;

import java.io.IOException;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.storage.ChannelExportLocation;
import ti4.service.storage.ChannelExportStatus;
import ti4.service.storage.ChannelExportWatcher;

class CancelChannelExport extends Subcommand {

    CancelChannelExport() {
        super(
                Constants.CANCEL_CHANNEL_EXPORT,
                "Stop a running channel export after its current part, keeping progress");
        addOptions(ChannelExportLookup.exportIdOption(), ChannelExportLookup.channelOption());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Optional<ChannelExportLocation> found = ChannelExportLookup.findForEvent(event);
        if (found.isEmpty()) return;
        ChannelExportLocation location = found.get();

        ChannelExportStatus status = location.status();
        if (status != ChannelExportStatus.RUNNING && status != ChannelExportStatus.NOT_STARTED) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Export `" + location.exportId() + "` is not running (" + describe(status) + ").");
            return;
        }

        try {
            location.requestCancel();
        } catch (IOException e) {
            BotLogger.error("Could not write the cancel request for channel export " + location.exportId(), e);
            MessageHelper.sendMessageToEventChannel(event, "Could not write the cancel request. Check the bot logs.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(
                event,
                "Asked export `" + location.exportId() + "` of <#" + location.channelId()
                        + "> to stop. It finishes the part it is on, keeps everything exported so far, and I'll"
                        + " post here once it has stopped.");
        ChannelExportWatcher.watch(location, event.getChannel(), ChannelExportLookup.maxUploadBytes(event));
    }

    private static String describe(ChannelExportStatus status) {
        return switch (status) {
            case CANCELLED -> "already cancelled";
            case PAUSED -> "it is paused at its size cap";
            case FAILED -> "it failed";
            case DONE -> "it already finished";
            case RUNNING, NOT_STARTED -> "running";
        };
    }
}
