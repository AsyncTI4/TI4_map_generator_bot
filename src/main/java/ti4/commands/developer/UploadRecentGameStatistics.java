package ti4.commands.developer;

import java.io.IOException;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.website.GameStatisticsUploadService;

class UploadRecentGameStatistics extends Subcommand {

    UploadRecentGameStatistics() {
        super(Constants.UPLOAD_RECENT_GAME_STATS, "Upload recently changed game statistics.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try {
            GameStatisticsUploadService.uploadRecentStats();
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Triggered upload of recently changed game statistics.");
        } catch (IOException e) {
            BotLogger.error("Failed to upload recently changed game statistics.", e);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Failed to upload recently changed game statistics.");
        }
    }
}
