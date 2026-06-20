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
class BoardingPartyAcd2ButtonHandler {

    @ButtonHandler("resolveBoardingParty")
    public static void resolveBoardingParty(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_skipbuild");
        String message = player.getRepresentation() + ", use the buttons to place the 1 ship you killed under 5 cost. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }
}
