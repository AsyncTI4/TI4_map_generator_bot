package ti4.buttons.handlers.hero;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class BenedictionButtonHandler {

    @ButtonHandler("mahactBenedictionFrom_")
    public static void mahactBenedictionFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperHeroes.mahactBenediction(buttonID, event, game, player);
        String pos1 = buttonID.split("_")[1];
        String pos2 = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmojiOrColor() + " moved all units in space from "
                + game.getTileByPosition(pos1).getRepresentationForButtons(game, player) + " to "
                + game.getTileByPosition(pos2).getRepresentationForButtons(game, player)
                + " using Airo Shir Aur, the Mahact hero. If they moved themselves and wish to move ground forces, they may do so either with slash command or modify units button.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("benedictionStep1_")
    public static void benedictionStep1(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos1 = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " choose the tile you wish to send the ships in "
                + game.getTileByPosition(pos1).getRepresentationForButtons(game, player) + " to.",
            ButtonHelperHeroes.getBenediction2ndTileOptions(player, game, pos1));
        ButtonHelper.deleteMessage(event);
    }
}
