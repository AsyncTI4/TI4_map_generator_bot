package ti4.helpers.async;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class CustomizationsHelper {
    @ButtonHandler("showHexBorders_")
    public static void editShowHexBorders(ButtonInteractionEvent event, Game game, String buttonID) {
        String value = buttonID.replace("showHexBorders_", "");
        game.setHexBorderStyle(value);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Updated Hex Border Style to `" + value + "`.\nIf you want to change this setting, use `/custom customization`");
        ButtonHelper.deleteMessage(event);
    }
}
