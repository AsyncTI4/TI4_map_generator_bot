package ti4.buttons.handlers.agenda;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.agenda.LookAgendaService;

@UtilityClass
class LookAgendaButtonHandler {

    @ButtonHandler("agendaLookAt") // agendaLookAt[count:X][lookAtBottom:Y] where X = int and Y = boolean
    public static void lookAtAgendas(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int count = Integer.parseInt(StringUtils.substringBetween(buttonID, "[count:", "]"));
        boolean lookAtBottom = Boolean.parseBoolean(StringUtils.substringBetween(buttonID, "[lookAtBottom:", "]"));
        LookAgendaService.lookAtAgendas(game, player, count, lookAtBottom);
        ButtonHelper.deleteMessage(event);
    }
}
