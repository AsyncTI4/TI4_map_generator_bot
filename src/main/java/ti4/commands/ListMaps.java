package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.generator.TilesMapper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.util.HashMap;
import java.util.stream.Collectors;

public class ListMaps implements Command {
    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        return msg.getContentRaw().startsWith(":list_maps");
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
        String mapNameList = "Map List:\n" + mapList.keySet().stream()
                .sorted()
                .collect(Collectors.joining("\n"));
        MessageHelper.replyToMessage(msg, mapNameList);
    }
}
