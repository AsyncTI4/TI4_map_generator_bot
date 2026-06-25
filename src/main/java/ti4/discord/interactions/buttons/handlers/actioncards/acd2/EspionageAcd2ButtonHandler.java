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

        // The target decides whether to allow the look BEFORE any cards are revealed.
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                target.factionButtonChecker() + "espionageAllow_" + player.getFaction(),
                "Reveal your action cards",
                CardEmojis.getACEmoji(target)));
        if (hasSendablePromissoryNote(target)) {
            buttons.add(Buttons.red(
                    target.factionButtonChecker() + "espionageSendPN_" + player.getFaction(),
                    "Send Random Promissory Note Instead",
                    CardEmojis.PN));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged() + ", "
                        + (game.isFowMode() ? "another player" : player.getFactionEmojiOrColor())
                        + " is resolving _Espionage_ against you. You may reveal your action cards so they can take 1,"
                        + " or send them a random promissory note instead.",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", _Espionage_ response buttons were sent to "
                        + target.getFactionEmojiOrColor() + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("espionageAllow_")
    public static void resolveEspionageAllow(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        // player == the target who is allowing the look; initiator is encoded in the buttonID.
        Player target = player;
        Player initiator = game.getPlayerFromColorOrFaction(buttonID.replace("espionageAllow_", ""));
        if (initiator == null || initiator == target) {
            MessageHelper.sendMessageToChannel(target.getCardsInfoThread(), "Could not resolve _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (target.getActionCards().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    initiator.getCorrectChannel(),
                    target.getFactionEmojiOrColor() + " has no action cards to choose for _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ActionCardHelper.showAll(target, initiator, game);
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : target.getActionCards().entrySet()) {
            buttons.add(Buttons.green(
                    initiator.factionButtonChecker() + "espionageChoose_" + target.getFaction() + "_"
                            + entry.getValue(),
                    Mapper.getActionCard(entry.getKey()).getName(),
                    CardEmojis.getACEmoji(target)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                initiator.getCardsInfoThread(),
                initiator.getRepresentationUnfogged() + ", choose which action card to take from "
                        + target.getColorIfCanSeeStats(initiator) + " for _Espionage_.",
                buttons);
        MessageHelper.sendMessageToChannel(
                initiator.getCorrectChannel(),
                initiator.getRepresentationUnfogged()
                        + ", _Espionage_ choices were sent to your `#cards-info` thread.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("espionageChoose_")
    public static void resolveEspionageChoose(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        // player == the initiator who is taking a card.
        Player initiator = player;
        String payload = buttonID.replace("espionageChoose_", "");
        int separator = payload.lastIndexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(initiator.getCardsInfoThread(), "Could not resolve _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(payload.substring(0, separator));
        Integer acIndex = parseInt(payload.substring(separator + 1));
        String actionCardId = getActionCardIdByIndex(target, acIndex);
        if (target == null || acIndex == null || actionCardId == null) {
            MessageHelper.sendMessageToChannel(
                    initiator.getCardsInfoThread(), "That action card is no longer available for _Espionage_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        target.removeActionCard(acIndex);
        initiator.setActionCard(actionCardId);
        ActionCardHelper.sendActionCardInfo(game, target);
        ActionCardHelper.sendActionCardInfo(game, initiator);
        ButtonHelper.checkACLimit(game, initiator);

        MessageHelper.sendMessageToChannel(
                initiator.getCardsInfoThread(),
                "# " + initiator.getRepresentation() + " you took the action card _"
                        + Mapper.getActionCard(actionCardId).getName() + "_ for _Espionage_.");
        MessageHelper.sendMessageToChannel(
                target.getCardsInfoThread(),
                "# " + target.getRepresentation() + " your action card _"
                        + Mapper.getActionCard(actionCardId).getName() + "_ was taken with _Espionage_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("espionageSendPN_")
    public static void resolveEspionageSendPromissoryNote(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        // player == the target refusing the look; receiver == the initiator.
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
