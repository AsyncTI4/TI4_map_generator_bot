package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class WillRevolutionAcd2ButtonHandler {

    @ButtonHandler("willRevolution")
    public static void willRevolution(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("willRevolution", "active");
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Reversed strategy card picking order.");
    }
}
