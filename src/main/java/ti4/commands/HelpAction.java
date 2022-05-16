package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.ResourceHelper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.message.MessageHelper;

import java.io.File;

public class HelpAction implements Command {

    @Override
    public String getActionID() {
        return Constants.HELP;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Help information is in help file");
        String helpFile = ResourceHelper.getInstance().getHelpFile("help.txt");
        if (helpFile != null){
            File file = new File(helpFile);
            if (!file.exists()){
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find help file");
                return;
            }
            MessageHelper.sendFileToChannel(event.getChannel(), file);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find help file");
        }
        MessageHelper.replyToMessageTI4Logo(event);
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Help Action")

        );
    }
}
