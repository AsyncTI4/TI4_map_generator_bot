package ti4.service.commodities;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class CommodityConversionService {

    public static void convertAllComm(ButtonInteractionEvent event, Player player, Game game) {
        String playerRep = player.getFactionEmoji();
        int commod = player.getCommodities();
        String message = playerRep + " converted their " + commod
                + " commodit" + (commod == 1 ? "y" : "ies") + " to "
                + (commod == 1 ? "a trade good" : commod + " trade goods") + " (trade goods: "
                + player.getTg() + "->" + (player.getTg() + commod) + ").";
        player.setTg(player.getTg() + commod);
        player.setCommodities(0);
        if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }
}
