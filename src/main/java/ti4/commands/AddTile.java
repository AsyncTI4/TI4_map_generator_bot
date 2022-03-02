package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ti4.ResourceHelper;
import ti4.generator.GenerateMap;
import ti4.generator.PositionMapper;
import ti4.generator.TilesMapper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.StringTokenizer;

public class AddTile implements Command {
    @Override
    public boolean accept(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        return msg.getContentRaw().startsWith(":add_tile");
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        String userID = event.getAuthor().getId();
        MapManager mapManager = MapManager.getInstance();
        if (!mapManager.isUserWithActiveMap(userID))
        {
            MessageHelper.replyToMessage(event.getMessage(),"Set your active map using: :set_map mapname");
        }
        else {
            Message msg = event.getMessage();
            String message = msg.getContentRaw();
            StringTokenizer tokenizer = new StringTokenizer(message, " ");
            if (tokenizer.countTokens() == 3)
            {
                String command = tokenizer.nextToken();//Left command parsing as we need to remove it for code
                String planetTileName = tokenizer.nextToken();
                String position = tokenizer.nextToken();
                if (!PositionMapper.isPositionValid(position)){
                    MessageHelper.replyToMessage(msg, "Position tile not allowed");
                    return;
                }

                String tileName = TilesMapper.getTileName(planetTileName);
                String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
                if (tilePath == null)
                {
                    MessageHelper.replyToMessage(msg, "Could not find tile");
                    return;
                }

                Tile tile = new Tile(planetTileName, position);
                Map userActiveMap = mapManager.getUserActiveMap(userID);
                userActiveMap.setTile(tile);

                MapSaveLoadManager.saveMap(userActiveMap);

                File file = GenerateMap.getInstance().saveImage(userActiveMap);
                MessageHelper.replyToMessage(event.getMessage(), file);
            }
        }
    }
}
