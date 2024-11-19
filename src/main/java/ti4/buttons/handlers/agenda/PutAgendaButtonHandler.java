package ti4.buttons.handlers.agenda;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

@UtilityClass
class PutAgendaButtonHandler {

    @ButtonHandler("topAgenda_")
    public static void topAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        AgendaHelper.putTop(Integer.parseInt(agendaNumID), game);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "top");
        } else {
            game.setStoredValue(key, game.getStoredValue(key) + "_top");
        }
        AgendaModel agenda = Mapper.getAgenda(game.lookAtTopAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
            "Put " + agenda.getName()
                + " on the top of the agenda deck. You may use this button to undo that and reassign it.",
            reassign);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bottomAgenda_")
    public static void bottomAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        AgendaHelper.putBottom(Integer.parseInt(agendaNumID), game);
        AgendaModel agenda = Mapper.getAgenda(game.lookAtBottomAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
            "Put " + agenda.getName()
                + " on the bottom of the agenda deck. You may use this button to undo that and reassign it.",
            reassign);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "bottom");
        } else {
            game.setStoredValue(key, game.getStoredValue(key) + "_bottom");
        }
        ButtonHelper.deleteMessage(event);
    }
}
