package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

import java.io.File;

public class HelpAction extends HelpSubcommandData {

    public HelpAction() {
        super(Constants.HELP_DOCUMENTATION, "Show Help Documentation");
    }

    @Override
    public String getActionID() {
        return Constants.HELP;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Help information is in help file");
        String helpFile = ResourceHelper.getInstance().getHelpFile("help.txt");
        if (helpFile != null) {
            File file = new File(helpFile);
            if (!file.exists()) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find help file");
                return;
            }
            MessageHelper.sendFileToChannel(event.getChannel(), file);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find help file");
        }
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
