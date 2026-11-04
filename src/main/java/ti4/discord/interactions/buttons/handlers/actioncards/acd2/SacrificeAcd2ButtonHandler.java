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
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
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
        for (Tile tile : game.getTileMap().values()) {
            if (tile != null && FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "sacrificeSystem_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you have no system that contains your units for"
                            + " _Sacrifice_.");
            return;
        }
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the system whose units you will sacrifice.",
                buttons);
    }

    @ButtonHandler("sacrificeSystem_")
    public static void resolveSacrificeSystem(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String position = buttonID.replace("sacrificeSystem_", "");
        Tile tile = game.getTileByPosition(position);
        ButtonHelper.deleteMessage(event);
        if (tile == null || !FoWHelper.playerHasUnitsInSystem(player, tile)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Sacrifice_.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "sacrificeReward_so_" + position,
                "Draw 1 Secret Objective",
                CardEmojis.SecretObjective));
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + "sacrificeReward_breakthrough_" + position, "Gain Your Breakthrough"));
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + "sacrificeReward_ingress_" + position,
                "Move an Ingress Token into the System"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose what to gain by destroying each of your units in "
                        + tile.getRepresentationForButtons(game, player) + ".",
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
        String position = payload.substring(separator + 1);
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || !FoWHelper.playerHasUnitsInSystem(player, tile)
                || !List.of("so", "breakthrough", "ingress").contains(reward)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Sacrifice_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyAllPlayerUnitsInSystem(event, game, player, tile, false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " destroyed each of their units in "
                        + tile.getRepresentationForButtons(game, player) + " for _Sacrifice_.");
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
        buttons.add(Buttons.CANCEL_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which ingress token to move into the system.",
                buttons);
    }
}
