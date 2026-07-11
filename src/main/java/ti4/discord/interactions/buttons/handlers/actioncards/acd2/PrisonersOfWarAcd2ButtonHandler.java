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
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

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
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(id, target.getColor()));
            } else {
                buttons.add(Buttons.gray(id, target.getFactionModel().getShortName())
                        .withEmoji(Emoji.fromFormatted(target.getFactionEmoji())));
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", there are no opponents to target with _Prisoners of War_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
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

        List<String> sendablePNs = sendablePromissoryNotes(target);
        if (sendablePNs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing()
                            + " has no promissory notes in hand to give for _Prisoners of War_. Continue by placing"
                            + " their command token.");
            sendCommandTokenButtons(player, game, target);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String pnId : sendablePNs) {
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + "prisonersOfWarGivePN_" + player.getFaction() + "_" + pnId,
                    Mapper.getPromissoryNote(pnId).getName(),
                    CardEmojis.PN));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", choose which promissory note to give to "
                        + (game.isFowMode() ? "your opponent" : player.getFactionEmojiOrColor())
                        + " for _Prisoners of War_.",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", " + target.getFactionEmojiOrColor()
                        + " was asked to give you a promissory note for _Prisoners of War_.");
    }

    @ButtonHandler("prisonersOfWarGivePN_")
    public static void resolvePrisonersOfWarGivePN(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        // player == the opponent giving the promissory note
        String[] parts = buttonID.replace("prisonersOfWarGivePN_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player receiver = game.getPlayerFromColorOrFaction(parts[0]);
        String pnId = parts[1];
        Integer pnIndex = player.getPromissoryNotes().get(pnId);
        if (receiver == null || pnIndex == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "That promissory note is no longer available for _Prisoners of War_.");
            return;
        }

        player.removePromissoryNote(pnIndex);
        receiver.setPromissoryNote(pnId);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, receiver, false);

        String pnName = Mapper.getPromissoryNote(pnId).getName();
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "# " + player.toString() + " you gave the promissory note _" + pnName + "_ for _Prisoners of War_.");
        MessageHelper.sendMessageToChannel(
                receiver.getCardsInfoThread(),
                "# " + receiver.toString() + " you gained the promissory note _" + pnName
                        + "_ from _Prisoners of War_.");

        sendCommandTokenButtons(receiver, game, player);
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
        for (Tile tile : game.getTiles()) {
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
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a non-home system containing your units to place "
                        + target.getRepresentationNoPing() + "'s command token in for _Prisoners of War_.",
                buttons);
    }

    private static List<String> sendablePromissoryNotes(Player player) {
        List<String> sendable = new ArrayList<>();
        for (String pn : player.getPromissoryNotes().keySet()) {
            if (!player.getPromissoryNotesInPlayArea().contains(pn)) {
                sendable.add(pn);
            }
        }
        return sendable;
    }
}
