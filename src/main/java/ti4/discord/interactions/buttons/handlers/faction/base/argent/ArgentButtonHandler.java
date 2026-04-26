package ti4.discord.interactions.buttons.handlers.faction.base.argent;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.message.MessageHelper;

@UtilityClass
class ArgentButtonHandler {

    @ButtonHandler("placeWingTransferCC_")
    public static void placeCC(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String position = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(position);
        String message =
                player.getRepresentationUnfogged() + " is using _Wing Transfer_ to place their command token in the "
                        + tile.getRepresentationForButtons() + " system.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        CommandCounterHelper.addCC(event, player, tile);
    }
}
