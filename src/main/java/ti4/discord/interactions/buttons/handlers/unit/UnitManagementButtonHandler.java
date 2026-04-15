package ti4.discord.interactions.buttons.handlers.unit;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.tactical.TacticalActionService;

@UtilityClass
class UnitManagementButtonHandler {

    @ButtonHandler("genericRemove_")
    public static void genericRemove(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericRemove_", "");

        game.getTacticalActionDisplacement().clear();
        List<Button> systemButtons = ButtonHelperTacticalAction.getButtonsForAllUnitsInSystem(
                player, game, game.getTileByPosition(pos), "Remove");

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Removing units from " + game.getTileByPosition(pos).getRepresentationForButtons(game, player) + ".");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please choose the units you wish to remove.", systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doActivation_")
    public static void doActivation(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("doActivation_", "");
        ButtonHelper.resolveOnActivationEnemyAbilities(game, game.getTileByPosition(pos), player, false, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericBuild_")
    public static void genericBuild(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericBuild_", "");
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "genericBuild", "place");
        String message = player.getRepresentation() + ", use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericModifyAllTiles_")
    public static void genericModifyAllTiles(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModifyAllTiles_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), "genericModifyAllTiles", "place");
        String message = player.getRepresentation() + ", use the buttons to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericModify_")
    public static void genericModify(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModify_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.offerBuildOrRemove(player, game, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("produceOneUnitInTile_")
    public static void produceOneUnitInTile(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        buttonID = buttonID.replace("produceOneUnitInTile_", "");
        String type = buttonID.split("_")[1];
        String pos = buttonID.split("_")[0];
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation() + ", use the buttons to produce 1 unit.\n> "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("removeAllStructures_")
    public static void removeAllStructures(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        plan.removeAllUnits(player.getColor());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Removed all units on " + planet + " for " + player.getRepresentation() + ".");
        AddPlanetToPlayAreaService.addPlanetToPlayArea(event, game.getTileFromPlanet(planet), planet, game);
    }

    @ButtonHandler("refreshLandingButtons")
    public static void refreshLandingButtons(ButtonInteractionEvent event, Player player, Game game) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> systemButtons = TacticalActionService.getLandingTroopsButtons(game, player, tile);
        event.getMessage()
                .editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("doneRemoving")
    public static void doneRemoving(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), event.getMessage().getContentRaw());
        ButtonHelper.deleteMessage(event);
        ButtonHelper.updateMap(game, event);
    }

    @ButtonHandler("getRepairButtons_")
    public static void getRepairButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("getRepairButtons_", "");
        List<Button> buttons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentationUnfogged() + ", use buttons to resolve.", buttons);
    }
}
