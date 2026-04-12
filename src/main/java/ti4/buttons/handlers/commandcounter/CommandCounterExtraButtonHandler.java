package ti4.buttons.handlers.commandcounter;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;

@UtilityClass
public class CommandCounterExtraButtonHandler {

    @ButtonHandler("reinforcements_cc_placement_")
    public static void reinforcementsCCPlacement(
            GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace("reinforcements_cc_placement_", "");
        String tileID = AliasHandler.resolveTile(planet.toLowerCase());
        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }
        CommandCounterHelper.addCC(event, player, tile);
        String message = player.getFactionEmojiOrColor() + " placed 1 command token from reinforcements in the "
                + Helper.getPlanetRepresentation(planet, game) + " system.";
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event);
        }
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gain_CC")
    public static void gainCC(ButtonInteractionEvent event, Player player, Game game) {
        String message = "";

        String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("confirm_cc")
    public static void confirmCC(ButtonInteractionEvent event, Game game, Player player) {
        String message = "Confirmed command tokens: " + player.getTacticalCC() + "/" + player.getFleetCC();
        if (!player.getMahactCC().isEmpty()) {
            message += "(+" + player.getMahactCC().size() + ")";
        }
        message += "/" + player.getStrategicCC();
        ReactionService.addReaction(event, game, player, true, false, message);
    }

    @ButtonHandler("placeCCBack_")
    public static void placeCCBack(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String position = buttonID.split("_")[1];
        String message = player.getRepresentationUnfogged()
                + " has chosen to not pay the 1 command token required to remove a command token from the Errant (Toldar flagship) system,"
                + " and so their command token has been placed back in tile " + position + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
        CommandCounterHelper.addCC(event, player, game.getTileByPosition(position));
    }

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

    @ButtonHandler("removeCCFromBoard_")
    public static void removeCCFromBoard(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.resolveRemovingYourCC(player, game, event, buttonID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mahactCommander")
    public static void mahactCommander(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "mahactCommander");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose which system you wish to remove your command token from.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }
}
