package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.util.ArrayList;

public class AddCC extends AddRemoveCC {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, ArrayList<String> colors, Tile tile) {
        for (String color : colors) {
            addCC(event, color, tile);
        }
    }

    public static void addCC(SlashCommandInteractionEvent event, String color, Tile tile) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        tile.addCC(ccID);
    }

    @Override
    protected String getActionDescription() {
        return "Add cc to tile/system";
    }

    @Override
    public String getActionID() {
        return Constants.ADD_CC;
    }
}
