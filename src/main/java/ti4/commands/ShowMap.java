package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.generator.GenerateMap;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.StringTokenizer;

public class ShowMap implements Command {

    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();

        if (msg.getContentRaw().startsWith(":show_map")) {
            StringTokenizer tokenizer = new StringTokenizer(msg.getContentRaw());
            if (tokenizer.countTokens() != 2)
            {
                MessageHelper.replyToMessage(msg, "Need to specify name for map. :show_map mapname");
                return false;
            }
            String setMap = tokenizer.nextToken(); //ignoring
            String mapName = tokenizer.nextToken();
            if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
                MessageHelper.replyToMessage(msg, "Map with such name does not exists, use :list_maps");
            }
            return true;
        }
        return false;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        StringTokenizer tokenizer = new StringTokenizer(msg.getContentRaw());
        String showMap = tokenizer.nextToken(); //ignoring
        String mapName = tokenizer.nextToken();
        Map map = MapManager.getInstance().getMap(mapName);
        File file = GenerateMap.getInstance().saveImage(map);
        MessageHelper.replyToMessage(event.getMessage(), file);
    }
}
