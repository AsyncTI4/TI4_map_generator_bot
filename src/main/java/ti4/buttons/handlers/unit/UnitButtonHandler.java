package ti4.buttons.handlers.unit;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.unit.AddUnitService;

import java.util.List;

@UtilityClass
class UnitButtonHandler {

    @ButtonHandler("genericBuild_")
    public static void genericBuild(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericBuild_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos),
            "genericBuild", "place");
        String message = player.getRepresentation() + ", use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericModifyAllTiles_")
    public static void genericModifyAllTiles(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModifyAllTiles_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos),
            "genericModifyAllTiles", "place");
        String message = player.getRepresentation() + ", use the buttons to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericModify_")
    public static void genericModify(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModify_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.offerBuildOrRemove(player, game, event, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("integratedBuild_")
    public static void integratedBuild(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int resources = 0;
        if (uH instanceof Planet plan) {
            resources = plan.getResources();
        }
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "integrated" + planet, "place");
        String message = player.getRepresentation()
            + " is using _Integrated Economy_ on " + Helper.getPlanetRepresentation(planet, game)
            + ". Use the buttons to produce units with a combined cost up to the planet (" + resources
            + ") resources.\n"
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Produce Units", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("produceOneUnitInTile_")
    public static void produceOneUnitInTile(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        buttonID = buttonID.replace("produceOneUnitInTile_", "");
        String type = buttonID.split("_")[1];
        String pos = buttonID.split("_")[0];
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type,
            "placeOneNDone_dontskip");
        String message = player.getRepresentation() + ", use the buttons to produce 1 unit.\n> "
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("winnuStructure_")
    public static void winnuStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("winnuStructure_", "").split("_")[0];
        String planet = buttonID.replace("winnuStructure_", "").split("_")[1];
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit + " " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, game) + ".");
        CommanderUnlockCheckService.checkPlayer(player, "titans", "saar", "rohdhna", "cheiran", "celdauri");
    }

    @ButtonHandler("qhetMechProduce_")
    public static void qhetMechProduce(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 inf " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " produced 2 infantry on " + Helper.getPlanetRepresentation(planet, game));
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Please pay one resource.", buttons);
    }

    @ButtonHandler("removeAllStructures_")
    public static void removeAllStructures(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        plan.removeAllUnits(player.getColor());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Removed all units on " + planet + " for " + player.getRepresentation() + ".");
        AddPlanetToPlayAreaService.addPlanetToPlayArea(event, game.getTileFromPlanet(planet), planet, game);
    }

    @ButtonHandler("jrStructure_")
    public static void jrStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("jrStructure_", "");
        if (!"tg".equalsIgnoreCase(unit)) {
            String message = player.getRepresentationUnfogged()
                + ", please choose the planet you wish to put your structure on.";
            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "placeOneNDone_dontskip");
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);

        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmojiOrColor() + " trade goods increased by 1 " + player.gainTG(1) + ".");
            ButtonHelper.deleteMessage(event);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dacxive_")
    public static void daxcive(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("dacxive_", "");
        AddUnitService.addUnits(event, game.getTile(AliasHandler.resolveTile(planet)), game, player.getColor(),
            "infantry " + planet);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmojiOrColor() + " placed 1 infantry on "
                + Helper.getPlanetRepresentation(planet, game) + " via _Dacxive Animators_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("deployTyrant")
    public static void deployTyrant(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to place the _Tyrant's Lament_ with your ships.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            Helper.getTileWithShipsPlaceUnitButtons(player, game, "tyrantslament", "placeOneNDone_skipbuild"));
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " is deploying the _Tyrant's Lament_.");
        player.addOwnedUnitByID("tyrantslament");
    }

    @ButtonHandler("refreshLandingButtons")
    public static void refreshLandingButtons(ButtonInteractionEvent event, Player player, Game game) {
        // This is implemented in TacticalActionService
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Refreshing landing buttons...");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getRepairButtons_")
    public static void getRepairButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        List<Button> buttons = ButtonHelper.getRepairButtons(player, game, game.getTileByPosition(pos));
        String message = player.getRepresentationUnfogged() + ", please choose which units you wish to repair.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resetProducedThings")
    public static void resetProducedThings(ButtonInteractionEvent event, Player player, Game game) {
        Helper.resetProducedUnits(player, game, event);
        event.getMessage().editMessage(Helper.buildProducedUnitsMessage(player, game)).queue();
    }
}