package ti4.buttons.handlers.faction.glimmer;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;

@UtilityClass
public class GlimmerButtonHandler {

    @ButtonHandler("endGlimmersRedTech_")
    public static void endGlimmersRedTech(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event != null) {
            ButtonHelper.deleteMessage(event);
        }
        String unit = buttonID.split("_")[1];

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the system adjacent to your destroyed unit that you wish to place the unit."
                        + "\n-# Note that not all options displayed are legal options. The bot did not check where the unit was destroyed.",
                Helper.getTileWithShipsPlaceUnitButtons(player, game, unit, "placeOneNDone_skipbuild"));
    }
}
