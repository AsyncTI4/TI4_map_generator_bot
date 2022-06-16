package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.util.ArrayList;

public class RemoveCC extends AddRemoveToken {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, ArrayList<String> colors, Tile tile, Map activeMap) {
        for (String color : colors) {
            String ccID = Mapper.getCCID(color);
            String ccPath = tile.getCCPath(ccID);
            if (ccPath == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
            }
            tile.removeCC(ccID);
        }
    }

    @Override
    protected String getActionDescription() {
        return "Remove cc from tile/system";
    }

    @Override
    public String getActionID() {
        return Constants.REMOVE_CC;
    }
}
