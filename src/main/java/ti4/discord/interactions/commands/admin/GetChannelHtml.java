package ti4.discord.interactions.commands.admin;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.Constants;
import ti4.helpers.RepositoryDispatchEvent;
import ti4.helpers.Storage;
import ti4.message.MessageHelper;

class GetChannelHtml extends Subcommand {

    GetChannelHtml() {
        super(Constants.GET_CHANNEL_HTML, "Dispatch the archive_game_channel GitHub action for a channel.");
        addOptions(new OptionData(
                        OptionType.CHANNEL, Constants.CHANNEL, "Channel to archive (defaults to current channel)")
                .setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping channelOption = event.getOption(Constants.CHANNEL);
        String channelId = channelOption != null
                ? channelOption.getAsChannel().getId()
                : event.getChannel().getId();

        Instant dispatchTime = Instant.now();
        new RepositoryDispatchEvent("archive_game_channel", Map.of("channel", channelId)).sendEvent();
        MessageHelper.sendMessageToEventChannel(
                event, "Dispatched `archive_game_channel` for channel <#" + channelId + ">.");

        MessageChannel responseChannel = event.getChannel();
        ExecutorServiceManager.runAsync("archive_game_channel_" + channelId, () -> {
            boolean success = RepositoryDispatchEvent.waitForWorkflowCompletion(
                    "archive_game_channel.yaml", dispatchTime, Duration.ofMinutes(10), channelId);
            if (!success) {
                MessageHelper.sendMessageToChannel(
                        responseChannel,
                        "The `archive_game_channel` workflow did not complete successfully. Check GitHub Actions for details.");
                return;
            }

            String storagePath = Storage.getStoragePath();
            if (storagePath == null) {
                MessageHelper.sendMessageToChannel(
                        responseChannel, "Storage path is not configured; cannot locate the exported HTML file.");
                return;
            }

            File exportDir = new File(storagePath + "/exported_channels");
            if (!exportDir.isDirectory()) {
                MessageHelper.sendMessageToChannel(
                        responseChannel, "The workflow completed but the exported_channels directory was not found.");
                return;
            }

            // discordchatexporter names files like "Guild - Channel [channelId].html"
            File[] matchingFiles = exportDir.listFiles(
                    f -> f.getName().contains(channelId) && f.getName().endsWith(".html"));

            if (matchingFiles == null || matchingFiles.length == 0) {
                MessageHelper.sendMessageToChannel(
                        responseChannel,
                        "The workflow completed but no exported HTML file was found for channel <#" + channelId + ">.");
                return;
            }

            File htmlFile = Arrays.stream(matchingFiles)
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(matchingFiles[0]);
            MessageHelper.sendFileToChannel(responseChannel, htmlFile);
        });
    }
}
