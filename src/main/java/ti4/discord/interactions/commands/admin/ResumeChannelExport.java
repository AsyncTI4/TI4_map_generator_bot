package ti4.discord.interactions.commands.admin;

import java.io.IOException;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.RepositoryDispatchEvent;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.storage.ChannelExportLocation;
import ti4.service.storage.ChannelExportRequest;
import ti4.service.storage.ChannelExportWatcher;

class ResumeChannelExport extends Subcommand {

    ResumeChannelExport() {
        super(Constants.RESUME_CHANNEL_EXPORT, "Continue a cancelled or failed channel export from where it stopped");
        addOptions(ChannelExportLookup.exportIdOption(), ChannelExportLookup.channelOption());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Optional<ChannelExportLocation> found = ChannelExportLookup.findForEvent(event);
        if (found.isEmpty()) return;
        ChannelExportLocation location = found.get();

        if (!location.canResume()) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Export `" + location.exportId() + "` can only be resumed after it was cancelled or failed"
                            + " partway through.");
            return;
        }

        String progress;
        try {
            progress = location.progressSummary()
                    .map(summary -> " (" + summary + " done)")
                    .orElse("");
            location.clearStopMarkers();
        } catch (IOException e) {
            BotLogger.error("Could not prepare channel export " + location.exportId() + " for resuming", e);
            MessageHelper.sendMessageToEventChannel(event, "Could not update the export's files. Check the bot logs.");
            return;
        }

        boolean dispatched = new RepositoryDispatchEvent(
                        ChannelExportRequest.DISPATCH_EVENT_TYPE, ChannelExportRequest.resumeDispatchPayload(location))
                .sendEvent();
        if (!dispatched) {
            markResumeFailed(location);
            MessageHelper.sendMessageToEventChannel(
                    event, "Could not start the `archive_game_channel` GitHub action. Check the bot logs.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(
                event,
                "Resuming export `" + location.exportId() + "` of <#" + location.channelId() + ">" + progress
                        + ". I'll post here when it finishes.");
        ChannelExportWatcher.watch(location, event.getChannel(), ChannelExportLookup.maxUploadBytes(event));
    }

    private static void markResumeFailed(ChannelExportLocation location) {
        try {
            location.markFailed("Resume could not be dispatched");
        } catch (IOException e) {
            BotLogger.error("Could not restore the failed marker for channel export " + location.exportId(), e);
        }
    }
}
