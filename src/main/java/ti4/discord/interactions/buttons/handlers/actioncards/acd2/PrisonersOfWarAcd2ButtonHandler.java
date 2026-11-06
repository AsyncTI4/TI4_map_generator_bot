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
import ti4.message.MessageHelper;

@UtilityClass
class PrisonersOfWarAcd2ButtonHandler {

    @ButtonHandler("resolvePrisonersOfWar")
    public static void resolvePrisonersOfWar(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == player) {
                continue;
            }
            String id = player.factionButtonChecker() + "prisonersOfWarTarget_" + target.getFaction();
            buttons.add(FoWHelper.fogSafeTargetButton(id, "gray", target));
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", there are no opponents to target with _Prisoners of War_.");
            return;
        }
        buttons.add(Buttons.CANCEL_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose your opponent for _Prisoners of War_.",
                buttons);
    }

    @ButtonHandler("prisonersOfWarTarget_")
    public static void resolvePrisonersOfWarTarget(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("prisonersOfWarTarget_", ""));
        ButtonHelper.deleteMessage(event);
        if (target == null || target == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Prisoners of War_.");
            return;
        }
        sendCommandTokenButtons(player, game, target);
    }

    @ButtonHandler("prisonersOfWarPlaceCC_")
    public static void resolvePrisonersOfWarPlaceCC(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("prisonersOfWarPlaceCC_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile destTile = game.getTileByPosition(parts[1]);
        if (target == null || destTile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Prisoners of War_.");
            return;
        }

        CommandCounterHelper.addCC(event, target, destTile);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + target.getRepresentationNoPing()
                        + "'s command token in " + destTile.getRepresentationForButtons(game, player)
                        + " for _Prisoners of War_.");
    }

    private static void sendCommandTokenButtons(Player player, Game game, Player target) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile != null && !tile.isHomeSystem(game) && FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "prisonersOfWarPlaceCC_" + target.getFaction() + "_"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no non-home system containing your units to place "
                            + target.getRepresentationNoPing() + "'s command token in for _Prisoners of War_.");
            return;
        }
        buttons.add(Buttons.CANCEL_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a non-home system containing your units to place "
                        + target.getRepresentationNoPing() + "'s command token in for _Prisoners of War_.",
                buttons);
    }
}
