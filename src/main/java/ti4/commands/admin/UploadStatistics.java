package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.WebHelper;
import ti4.message.MessageHelper;

public class UploadStatistics extends AdminSubcommandData {

    public UploadStatistics() {
        super("upload_statistics", "Uploads the statistics to the s3 bucket");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        WebHelper.putStats();
        MessageHelper.sendMessageToEventChannel(event, "Uploaded Statistics");
    }

    
}
