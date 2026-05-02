package ti4.discord.interactions.buttons.handlers.faction.base.arborec;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
class ArborecButtonHandlers {

    @ButtonHandler("arboAgentOn_")
    public static void arboAgentOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(
                player, game, event, game.getTileByPosition(pos), unit);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you wish to place down.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arboAgentIn_")
    public static void arboAgentIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf('_') + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you'd like to replace.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arboAgentPutShip_")
    public static void arboAgentPutShip(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unitNPlace = buttonID.replace("arboAgentPutShip_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String successMessage = player.getFactionEmojiOrColor() + " Replaced a ship with 1 ";
        switch (unit) {
            case "destroyer" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "destroyer");
                successMessage += UnitEmojis.destroyer;
            }
            case "cruiser" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "cruiser");
                successMessage += UnitEmojis.cruiser;
            }
            case "carrier" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "carrier");
                successMessage += UnitEmojis.carrier;
            }
            case "dreadnought" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "dreadnought");
                successMessage += UnitEmojis.dreadnought;
            }
            case "fighter" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "fighter");
                successMessage += UnitEmojis.fighter;
            }
            case "warsun" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "warsun");
                successMessage += UnitEmojis.warsun;
            }
        }
        successMessage += " in tile " + tile.getRepresentationForButtons(game, player);

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arboCommanderBuild_")
    public static void arboCommanderBuild(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("arboCommanderBuild_", "");
        List<Button> buttons;
        Tile tile = TileHelper.getTile(event, planet, game);
        buttons = Helper.getPlaceUnitButtons(
                event, player, game, tile, "arboCommander", "placeOneNDone_dontskiparboCommander");
        String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }
}
