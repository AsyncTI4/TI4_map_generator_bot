package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
public class DeepTechHandler {
    private static final String IT_LOOKS_BACK = "bedeepg";

    public static void startAbyssalGenesis(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasShipsInSystem(player, tile)
                    || FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "produceAbyssalGenesis_" + tile.getPosition(),
                    "Produce in " + tile.getRepresentationForButtons(game, player)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", there are no eligible systems for _Abyssal Genesis_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please select the system in which you wish to produce using _Abyssal Genesis_",
                buttons);
    }

    @ButtonHandler("produceAbyssalGenesis_")
    public static void resolveAbyssalGenesis(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace("produceAbyssalGenesis_", "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null
                || !FoWHelper.playerHasShipsInSystem(player, tile)
                || FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may produce up to 2 units using _Abyssal Genesis_.",
                Helper.getPlaceUnitButtons(event, player, game, tile, "deepTech", "place"));

        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerItLooksBack(Game game, Player producer, String productionMessageJumpUrl) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasTechReady(IT_LOOKS_BACK)
                    || !player.getAbilityNeighbours().contains(producer)) {
                continue;
            }
            List<Button> buttons = List.of(
                    Buttons.green(
                            player.factionButtonChecker() + "exhaustTech_" + IT_LOOKS_BACK, "Exhaust It Looks Back"),
                    Buttons.red(player.factionButtonChecker() + "declineItLooksBack", "Decline"));
            String message = player.getRepresentationNoPing()
                    + ", "
                    + (game.isFowMode() ? "another player" : producer.getRepresentationNoPing())
                    + " produced units. You may exhaust _It Looks Back_ to produce 1 unit.";
            if (!game.isFowMode()) {
                message += "\n-# [View production message](" + productionMessageJumpUrl + ")";
            }
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        }
    }

    public static void resolveItLooksBack(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!hasProductionUnit(player, tile)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "itLooksBackSystem_" + tile.getPosition(),
                    "Produce in " + tile.getRepresentationForButtons(game, player)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", you have no systems containing one of your units with PRODUCTION.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted _It Looks Back_. Select the system in which to produce 1 unit.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("declineItLooksBack")
    public static void declineItLooksBack(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("itLooksBackSystem_")
    public static void selectItLooksBackSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.replace("itLooksBackSystem_", ""));
        if (tile == null || !hasProductionUnit(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, tile, "itLooksBack", "placeOneNDone_dontskip");
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", you cannot currently produce a unit in that system.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", produce 1 unit using _It Looks Back_. You must pay its cost.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasProductionUnit(Player player, Tile tile) {
        return tile.containsPlayersUnitsWithModelCondition(
                player, unit -> unit.getProductionValue() > 0 || unit.getBasicProduction() != null);
    }
}
