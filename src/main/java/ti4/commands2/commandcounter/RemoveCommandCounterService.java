package ti4.commands2.commandcounter;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class RemoveCommandCounterService {

    public static void removeCC(GenericInteractionCreateEvent event, String color, Tile tile, Game game) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        if (game.isFowMode()) {
            String colorMention = Emojis.getColorEmojiWithName(color);
            FoWHelper.pingSystem(game, event, tile.getPosition(), colorMention + " has removed a token in the system");
        }
        tile.removeCC(ccID);
    }
}
