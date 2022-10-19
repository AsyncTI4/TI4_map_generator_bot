package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

import java.io.File;

public class SetupTemplatesAction extends HelpSubcommandData {

    public SetupTemplatesAction() {
        super(Constants.SETUP_TEMPLATES, "Show Setup Templates");
    }

    @Override
    public String getActionID() {
        return Constants.HELP;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Setup Templates file");
        String helpFile = ResourceHelper.getInstance().getHelpFile("setup_templates.txt");
        if (helpFile != null) {
            File file = new File(helpFile);
            if (!file.exists()) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find setup templates file");
                return;
            }
            MessageHelper.sendFileToChannel(event.getChannel(), file);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find setup templates file");
        }
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
