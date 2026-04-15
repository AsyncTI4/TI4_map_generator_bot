package ti4.discord.interactions.buttons.handlers.planet;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class MalliceButtonHandler {

    @ButtonHandler("mallice_convert_comm")
    public static void malliceConvertComm(ButtonInteractionEvent event, Player player, Game game) {
        String playerRep = player.getFactionEmoji();
        int commod = player.getCommodities();
        String message = playerRep + " exhausted Mallice ability to convert their " + commod
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

    @ButtonHandler("mallice_2_tg")
    public static void mallice2tg(ButtonInteractionEvent event, Player player, Game game) {
        String playerRep = player.getFactionEmoji();
        String message = playerRep + " exhausted Mallice ability and gained trade goods " + player.gainTG(2) + ".";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 2);
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }
}
