package ti4.buttons.handlers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class TrapButtonHandler {

    @ButtonHandler("steal2tg_")
    public static void steal2Tg(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int count = Math.min(p2.getTg(), 2);
        p2.setTg(p2.getTg() - count);
        player.setTg(player.getTg() + count);
        String msg1 = p2.getRepresentationUnfogged() + " you had " + count + " TG" + (count == 1 ? "" : "s") + " stolen by a trap";
        String msg2 = player.getRepresentationUnfogged() + " you stole " + count + " TG" + (count == 1 ? "" : "s") + " via a trap";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("steal3comm_")
    public static void steal3Comm(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int count = Math.min(p2.getCommodities(), 3);
        p2.setCommodities(p2.getCommodities() - count);
        player.setTg(player.getTg() + count);
        String msg1 = p2.getRepresentationUnfogged() + " you had " + count + " comm" + (count == 1 ? "" : "s") + " stolen by a trap";
        String msg2 = player.getRepresentationUnfogged() + " you stole " + count + " comm" + (count == 1 ? "" : "s") + " via a trap";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        ButtonHelper.deleteMessage(event);
    }
}
