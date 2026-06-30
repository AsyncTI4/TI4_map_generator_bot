package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.objectives.DrawSecretService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
class SacrificeAcd2ButtonHandler {

    @ButtonHandler("resolveSacrifice")
    public static void resolveSacrifice(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet uH = game.getUnitHolderFromPlanet(planet);
            if (uH != null && uH.getUnitCount(player.getColor()) >= 2) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "sacrificePlanet_" + planet,
                        Helper.getPlanetRepresentation(planet, game)));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you do not control a planet that contains 2 of your units"
                            + " for _Sacrifice_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the planet whose units you will sacrifice.",
                buttons);
    }

    @ButtonHandler("sacrificePlanet_")
    public static void resolveSacrificePlanet(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("sacrificePlanet_", "");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "sacrificeReward_so_" + planet,
                "Draw 1 Secret Objective",
                CardEmojis.SecretObjective));
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + "sacrificeReward_breakthrough_" + planet, "Gain Your Breakthrough"));
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + "sacrificeReward_ingress_" + planet,
                "Move an Ingress Token into the System"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose what to gain by sacrificing your units on "
                        + Helper.getPlanetRepresentation(planet, game) + ".",
                buttons);
    }

    @ButtonHandler("sacrificeReward_")
    public static void resolveSacrificeReward(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("sacrificeReward_", "");
        int separator = payload.indexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Sacrifice_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String reward = payload.substring(0, separator);
        String planet = payload.substring(separator + 1);
        Planet unitHolder = game.getUnitHolderFromPlanet(planet);
        Tile tile = game.getTileFromPlanet(planet);
        if (unitHolder == null || tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Sacrifice_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyAllPlayerUnits(event, game, player, tile, unitHolder, false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " destroyed all of their units on "
                        + Helper.getPlanetRepresentation(planet, game) + " for _Sacrifice_.");
        ButtonHelper.deleteMessage(event);

        switch (reward) {
            case "so" -> DrawSecretService.drawSO(event, game, player);
            case "breakthrough" -> {
                List<String> locked = player.getBreakthroughIDs().stream()
                        .filter(bt -> !player.isBreakthroughUnlocked(bt))
                        .toList();
                if (locked.isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged()
                                    + " has no locked breakthrough to gain for _Sacrifice_.");
                } else {
                    BreakthroughCommandHelper.unlockBreakthroughs(game, player, locked);
                }
            }
            case "ingress" -> sendIngressFromButtons(player, game, tile.getPosition());
            default ->
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Unknown _Sacrifice_ reward choice.");
        }
    }

    @ButtonHandler("sacrificeIngressFrom_")
    public static void resolveSacrificeIngressFrom(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("sacrificeIngressFrom_", "").split("_", 2);
        if (parts.length < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String targetPos = parts[0];
        String fromPos = parts[1];
        Tile fromTile = game.getTileByPosition(fromPos);
        Tile toTile = game.getTileByPosition(targetPos);
        ButtonHelper.deleteMessage(event);
        if (fromTile == null || toTile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not move the ingress token.");
            return;
        }
        fromTile.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
        toTile.addToken(Constants.TOKEN_INGRESS, "space");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " moved an ingress token from "
                        + fromTile.getRepresentationForButtons(game, player) + " to "
                        + toTile.getRepresentationForButtons(game, player) + " for _Sacrifice_.");
    }

    private static void sendIngressFromButtons(Player player, Game game, String targetPos) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile != null && tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "sacrificeIngressFrom_" + targetPos + "_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", there are no ingress tokens on the board to move for"
                            + " _Sacrifice_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which ingress token to move into the system.",
                buttons);
    }
}
