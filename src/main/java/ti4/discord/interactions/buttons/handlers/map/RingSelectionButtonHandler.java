package ti4.discord.interactions.buttons.handlers.map;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class RingSelectionButtonHandler {

    @ButtonHandler("ring_")
    public static void ring(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> ringButtons = ButtonHelper.getTileInARing(player, game, buttonID);
        String num = buttonID.replace("ring_", "");
        String message;
        if (!"corners".equalsIgnoreCase(num)) {
            int ring = Integer.parseInt(num.charAt(0) + "");
            if (ring > 4 && !num.contains("left") && !num.contains("right")) {
                message =
                        "That ring is very large. Specify if your tile is on the left or right side of the map (center will be counted in both).";
            } else {
                message = "Please choose the system that you wish to activate.";
            }
        } else {
            message = "Please choose the system that you wish to activate.";
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        ButtonHelper.deleteMessage(event);
    }
}
