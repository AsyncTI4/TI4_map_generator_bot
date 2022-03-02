package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.util.StringTokenizer;

public class CreateMap implements Command {

    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        if (msg.getContentRaw().startsWith(":create_map")) {
            StringTokenizer tokenizer = new StringTokenizer(msg.getContentRaw());
            String createMap = tokenizer.nextToken(); //ignoring
            String mapName = tokenizer.nextToken();
            if (MapManager.getInstance().getMapList().containsKey(mapName)) {
                MessageHelper.replyToMessage(msg, "Map with such name exist already, choose different name");
            }
            return true;
        }
        return false;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        StringTokenizer tokenizer = new StringTokenizer(msg.getContentRaw());
        String createMap = tokenizer.nextToken(); //ignoring
        String mapName = tokenizer.nextToken();

        Map map = new Map();
        String ownerID = event.getAuthor().getId();
        map.setOwnerID(ownerID);
        map.setName(mapName);


        MapManager mapManager = MapManager.getInstance();
        mapManager.addMap(map);
        boolean setMapSuccessful = mapManager.setMapForUser(ownerID, mapName);
        if (!setMapSuccessful)
        {
            MessageHelper.replyToMessage(event.getMessage(), "Could not assign active map " + mapName);
        }
        MessageHelper.replyToMessage(event.getMessage(), "Map created with name: " + mapName);
    }
}
