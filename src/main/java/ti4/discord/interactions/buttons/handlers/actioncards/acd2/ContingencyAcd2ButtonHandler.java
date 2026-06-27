package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class ContingencyAcd2ButtonHandler {

    @ButtonHandler("resolveContingency")
    public static void resolveContingency(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskipcontingency");
        String message = player.getRepresentation()
                + ", use the buttons to place up to 2 ships that have a combined cost of 3 or less.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }
}
