package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class Shutdown extends AdminSubcommandData {

    public Shutdown() {
        super(Constants.SHUTDOWN, "Shut down TI4 Game Management Bot");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.replyToMessage(event, "Shutdown accepted");
        MapGenerator.jda.shutdownNow();
    }
}
