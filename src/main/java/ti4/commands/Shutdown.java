package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.MapGenerator;

public class Shutdown implements Command{

    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        return msg.getContentRaw().startsWith(":shutdown");
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        MapGenerator.jda.shutdownNow();
    }
}
