package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
class EspionageAcd2ButtonHandler {

    @ButtonHandler("resolveEspionage")
    public static void resolveEspionage(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == player || target.getActionCards().isEmpty()) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "espionageTarget_" + target.getFaction(), target.getColor()));
            } else {
                Button button = Buttons.gray(
                        player.factionButtonChecker() + "espionageTarget_" + target.getFaction(),
                        target.getFactionModel().getShortName());
                button = button.withEmoji(Emoji.fromFormatted(target.getFactionEmoji()));
                buttons.add(button);
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no other players with action cards to target with _Espionage_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which player to target with _Espionage_.",
                buttons);
    }

    @ButtonHandler("espionageTarget_")
    public static void resolveEspionageTarget(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("espionageTarget_", ""));
        if (target == null || target == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (target.getActionCards().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getFactionEmojiOrColor() + " has no action cards to choose for _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ActionCardHelper.showAll(target, player, game);
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : target.getActionCards().entrySet()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "espionageChoose_" + target.getFaction() + "_" + entry.getValue(),
                    Mapper.getActionCard(entry.getKey()).getName(),
                    CardEmojis.getACEmoji(target)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose which action card to request from "
                        + target.getColorIfCanSeeStats(player) + " for _Espionage_.",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", _Espionage_ choices were sent to your `#cards-info` thread.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("espionageChoose_")
    public static void resolveEspionageChoose(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("espionageChoose_", "");
        int separator = payload.lastIndexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(payload.substring(0, separator));
        Integer acIndex = parseInt(payload.substring(separator + 1));
        String actionCardId = getActionCardIdByIndex(target, acIndex);
        if (target == null || acIndex == null || actionCardId == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "That action card is no longer available for _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                target.factionButtonChecker() + "espionageSendAC_" + player.getFaction() + "_" + acIndex,
                "Send " + Mapper.getActionCard(actionCardId).getName(),
                CardEmojis.getACEmoji(target)));
        if (hasSendablePromissoryNote(target)) {
            buttons.add(Buttons.red(
                    target.factionButtonChecker() + "espionageSendPN_" + player.getFaction(),
                    "Send Random Promissory Note",
                    CardEmojis.PN));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", _Espionage_ selected your _"
                        + Mapper.getActionCard(actionCardId).getName() + "_. You may give that card to "
                        + (game.isFowMode() ? "another player" : player.getFactionEmojiOrColor())
                        + ", or send a random promissory note instead.",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", _Espionage_ response buttons were sent to "
                        + target.getFactionEmojiOrColor() + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("espionageSendAC_")
    public static void resolveEspionageSendActionCard(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("espionageSendAC_", "");
        int separator = payload.lastIndexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not resolve _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player receiver = game.getPlayerFromColorOrFaction(payload.substring(0, separator));
        Integer acIndex = parseInt(payload.substring(separator + 1));
        String actionCardId = getActionCardIdByIndex(player, acIndex);
        if (receiver == null || acIndex == null || actionCardId == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "That action card is no longer available for _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removeActionCard(acIndex);
        receiver.setActionCard(actionCardId);
        ActionCardHelper.sendActionCardInfo(game, player);
        ActionCardHelper.sendActionCardInfo(game, receiver);
        ButtonHelper.checkACLimit(game, receiver);

        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "# " + player.getRepresentation() + " you gave the action card _"
                        + Mapper.getActionCard(actionCardId).getName() + "_ for _Espionage_.");
        MessageHelper.sendMessageToChannel(
                receiver.getCardsInfoThread(),
                "# " + receiver.getRepresentation() + " you gained the action card _"
                        + Mapper.getActionCard(actionCardId).getName() + "_ from _Espionage_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("espionageSendPN_")
    public static void resolveEspionageSendPromissoryNote(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player receiver = game.getPlayerFromColorOrFaction(buttonID.replace("espionageSendPN_", ""));
        if (receiver == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not resolve _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!hasSendablePromissoryNote(player)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", you have no promissory notes to send for _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        PromissoryNoteHelper.sendRandom(event, game, player, receiver);
        ButtonHelper.deleteMessage(event);
    }

    private static String getActionCardIdByIndex(Player player, Integer acIndex) {
        if (player == null || acIndex == null) {
            return null;
        }
        for (Map.Entry<String, Integer> entry : player.getActionCards().entrySet()) {
            if (entry.getValue().equals(acIndex)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean hasSendablePromissoryNote(Player player) {
        if (player == null) {
            return false;
        }
        return player.getPromissoryNotes().keySet().stream()
                .anyMatch(pn -> !player.getPromissoryNotesInPlayArea().contains(pn));
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
