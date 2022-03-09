package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class RemoveCC extends AddRemoveCC {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, String color, Tile tile) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
            return;
        }
        tile.removeCC(ccID);
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
