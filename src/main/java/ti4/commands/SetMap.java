package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.MapGenerator;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.util.StringTokenizer;

public class SetMap implements Command {

    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        return msg.getContentRaw().startsWith(":set_map");
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        String userID = event.getAuthor().getId();

        Message msg = event.getMessage();
        StringTokenizer tokenizer = new StringTokenizer(msg.getContentRaw());
        String setMap = tokenizer.nextToken(); //ignoring
        String mapName = tokenizer.nextToken();
        boolean setMapSuccessful = MapManager.getInstance().setMapForUser(userID, mapName);
        if (!setMapSuccessful)
        {
            MessageHelper.replyToMessage(event.getMessage(), "Could not assign active map " + mapName);
        }
    }
}
