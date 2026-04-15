package ti4.discord.interactions.buttons.handlers.relics;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
class HomebrewRelicButtonHandler {

    @ButtonHandler("deployTyrant")
    public static void deployTyrant(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to place the _Tyrant's Lament_ with your ships.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "tyrantslament", "placeOneNDone_skipbuild"));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " is deploying the _Tyrant's Lament_.");
        player.addOwnedUnitByID("tyrantslament");
    }

    @ButtonHandler("thronePoint")
    public static void thronePoint(ButtonInteractionEvent event, Player player, Game game) {
        Integer poIndex = game.addCustomPO("Throne of the False Emperor", 1);
        game.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " scored a secret objective (they'll specify which one). The bot has already given you a victory point for this.");
        Helper.checkEndGame(game, player);
        ButtonHelper.deleteMessage(event);
    }
}
