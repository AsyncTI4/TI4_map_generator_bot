package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
class MobilizationAcd2ButtonHandler {

    @ButtonHandler("resolveMobilization")
    public static void resolveMobilization(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile == null) {
                continue;
            }
            if (CommandCounterHelper.hasCC(player, tile)
                    && !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "mobilizationProduce_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no system that contains 1 of your command tokens and no other players' units"
                            + " for _Mobilization_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the system to produce in for _Mobilization_. It must"
                        + " contain 1 of your command tokens and no other players' units.",
                buttons);
    }

    @ButtonHandler("mobilizationProduce_")
    public static void resolveMobilizationProduce(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("mobilizationProduce_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.deleteMessage(event);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Mobilization_.");
            return;
        }
        if (!CommandCounterHelper.hasCC(player, tile)
                || FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "That system no longer contains 1 of your command tokens and no other players' units"
                            + " for _Mobilization_.");
            return;
        }

        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "warfare", "place");
        String message = player.getRepresentation() + ", use the buttons to produce up to 4 units in "
                + tile.getRepresentationForButtons(game, player) + " for _Mobilization_."
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game, true);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }
}
