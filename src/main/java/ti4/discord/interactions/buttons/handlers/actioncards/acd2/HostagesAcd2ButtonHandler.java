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
class HostagesAcd2ButtonHandler {

    @ButtonHandler("resolveHostages")
    public static void resolveHostages(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == player) {
                continue;
            }
            String id = player.factionButtonChecker() + "hostagesTarget_" + target.getFaction();
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
                    player.getRepresentationUnfogged() + ", there are no opponents to target with _Hostages_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose your opponent for _Hostages_.",
                buttons);
    }

    @ButtonHandler("hostagesTarget_")
    public static void resolveHostagesTarget(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("hostagesTarget_", ""));
        ButtonHelper.deleteMessage(event);
        if (target == null || target == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Hostages_.");
            return;
        }

        List<String> sendablePNs = sendablePromissoryNotes(target);
        if (sendablePNs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing()
                            + " has no promissory notes in hand to give for _Hostages_. Continue by placing their"
                            + " command token.");
            sendCommandTokenButtons(player, game, target);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String pnId : sendablePNs) {
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + "hostagesGivePN_" + player.getFaction() + "_" + pnId,
                    Mapper.getPromissoryNote(pnId).getName(),
                    CardEmojis.PN));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", choose which promissory note to give to "
                        + (game.isFowMode() ? "your opponent" : player.getFactionEmojiOrColor()) + " for _Hostages_.",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", " + target.getFactionEmojiOrColor()
                        + " was asked to give you a promissory note for _Hostages_.");
    }

    @ButtonHandler("hostagesGivePN_")
    public static void resolveHostagesGivePN(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        // player == the opponent giving the promissory note
        String[] parts = buttonID.replace("hostagesGivePN_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player receiver = game.getPlayerFromColorOrFaction(parts[0]);
        String pnId = parts[1];
        Integer pnIndex = player.getPromissoryNotes().get(pnId);
        if (receiver == null || pnIndex == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "That promissory note is no longer available for _Hostages_.");
            return;
        }

        player.removePromissoryNote(pnIndex);
        receiver.setPromissoryNote(pnId);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, receiver, false);

        String pnName = Mapper.getPromissoryNote(pnId).getName();
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "# " + player.getRepresentation() + " you gave the promissory note _" + pnName + "_ for _Hostages_.");
        MessageHelper.sendMessageToChannel(
                receiver.getCardsInfoThread(),
                "# " + receiver.getRepresentation() + " you gained the promissory note _" + pnName
                        + "_ from _Hostages_.");

        sendCommandTokenButtons(receiver, game, player);
    }

    @ButtonHandler("hostagesPlaceCC_")
    public static void resolveHostagesPlaceCC(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("hostagesPlaceCC_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile destTile = game.getTileByPosition(parts[1]);
        if (target == null || destTile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Hostages_.");
            return;
        }

        CommandCounterHelper.addCC(event, target, destTile);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + target.getRepresentationNoPing()
                        + "'s command token in " + destTile.getRepresentationForButtons(game, player)
                        + " for _Hostages_.");
    }

    private static void sendCommandTokenButtons(Player player, Game game, Player target) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile != null && !tile.isHomeSystem(game) && FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "hostagesPlaceCC_" + target.getFaction() + "_"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you have no non-home system containing your units to place "
                            + target.getRepresentationNoPing() + "'s command token in for _Hostages_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a non-home system containing your units to place "
                        + target.getRepresentationNoPing() + "'s command token in for _Hostages_.",
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
