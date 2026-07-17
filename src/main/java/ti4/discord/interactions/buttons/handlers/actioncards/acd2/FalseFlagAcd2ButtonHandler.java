package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;

@UtilityClass
class FalseFlagAcd2ButtonHandler {

    @ButtonHandler("resolveFalseFlag")
    public static void resolveFalseFlag(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !hasAnyCommandToken(game, p2)) {
                continue;
            }
            String id = player.factionButtonChecker() + "falseFlagPlayer_" + p2.getFaction();
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(id, p2.getColor()));
            } else {
                buttons.add(Buttons.gray(id, p2.getFactionModel().getShortName())
                        .withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", no other player has a command token on the board for _False Flag_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose whose command token to remove for _False Flag_.",
                buttons);
    }

    @ButtonHandler("falseFlagPlayer_")
    public static void resolveFalseFlagPlayer(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("falseFlagPlayer_", ""));
        ButtonHelper.deleteMessage(event);
        if (target == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _False Flag_.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile != null && CommandCounterHelper.hasCC(target, tile)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "falseFlagFrom_" + target.getFaction() + "_"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no command tokens on the board for _False Flag_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which of " + target.getRepresentationNoPing()
                        + "'s command tokens to remove for _False Flag_.",
                buttons);
    }

    @ButtonHandler("falseFlagFrom_")
    public static void resolveFalseFlagFrom(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("falseFlagFrom_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile fromTile = game.getTileByPosition(parts[1]);
        if (target == null || fromTile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _False Flag_.");
            return;
        }

        RemoveCommandCounterService.fromTile(event, target, fromTile);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " removed " + target.getRepresentationNoPing()
                        + "'s command token from " + fromTile.getRepresentationForButtons(game, player)
                        + " for _False Flag_.");

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "falseFlagTo_" + target.getFaction() + "_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no systems containing your units to place the token in for _False Flag_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a system containing your units to place "
                        + target.getRepresentationNoPing() + "'s command token in for _False Flag_.",
                buttons);
    }

    @ButtonHandler("falseFlagTo_")
    public static void resolveFalseFlagTo(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("falseFlagTo_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile destTile = game.getTileByPosition(parts[1]);
        if (target == null || destTile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _False Flag_.");
            return;
        }

        CommandCounterHelper.addCC(event, target, destTile);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + target.getRepresentationNoPing()
                        + "'s command token in " + destTile.getRepresentationForButtons(game, player)
                        + " for _False Flag_.");
    }

    private static boolean hasAnyCommandToken(Game game, Player player) {
        for (Tile tile : game.getTileMap().values()) {
            if (tile != null && CommandCounterHelper.hasCC(player, tile)) {
                return true;
            }
        }
        return false;
    }
}
