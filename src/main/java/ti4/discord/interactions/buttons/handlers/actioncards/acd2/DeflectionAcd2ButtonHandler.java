package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
class DeflectionAcd2ButtonHandler {

    @ButtonHandler("deflectSC_")
    public static void deflectSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.split("_")[1];
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("deflectedSC", sc);
        // TODO: move this out of here.
        if (game.isTwilightsFallMode()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Put _Tartarus_ on **" + Helper.getSCName(Integer.parseInt(sc), game) + "**.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Put _Deflection_ on **" + Helper.getSCName(Integer.parseInt(sc), game) + "**.");
        }
    }
}
