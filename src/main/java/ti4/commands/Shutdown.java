package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.MapGenerator;
import ti4.message.MessageHelper;

public class Shutdown implements Command {

    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        return msg.getContentRaw().startsWith(":shutdown");
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        if (event.getAuthor().getId().equals(MapGenerator.userID)) {
            MapGenerator.jda.shutdownNow();
        } else {
            MessageHelper.replyToMessage(event.getMessage(), "Not Authorized shutdown attempt");
        }
    }
}
