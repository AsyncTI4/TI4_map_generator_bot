package ti4.discord.interactions.buttons.handlers.strategycard;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
class DiplomacyStrategyCardButtonHandler {

    @ButtonHandler("diploSystem")
    public static void diploSystem(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", please choose the system you wish to Diplo.";
        List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
    }
}
