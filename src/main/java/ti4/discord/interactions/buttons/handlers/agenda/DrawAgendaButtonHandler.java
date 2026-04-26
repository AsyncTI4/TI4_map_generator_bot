package ti4.discord.interactions.buttons.handlers.agenda;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class DrawAgendaButtonHandler {

    @ButtonHandler("drawAgenda_2")
    public static void drawAgenda2(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.getStoredValue("hasntSetSpeaker").isEmpty() && !game.isHomebrewSCMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you need to assign speaker first before drawing agendas. You can override this restriction with `/agenda draw`.");
            return;
        }
        AgendaHelper.drawAgenda(2, game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentation(true, false) + " drew 2 agendas");
        ButtonHelper.deleteMessage(event);
    }
}
