package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
class BetrayalAcd2ButtonHandler {

    @ButtonHandler("resolveBetrayal")
    public static void resolveBetrayal(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("resolveBetrayalStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "resolveBetrayalStep2_" + p2.getFaction(),
                        p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose which player will receive your promissory note for _Betrayal_.",
                buttons);
    }

    @ButtonHandler("resolveBetrayalStep2_")
    public static void resolveBetrayalStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player chosenPlayer = game.getPlayerFromColorOrFaction(buttonID.replace("resolveBetrayalStep2_", ""));
        if (chosenPlayer == null || chosenPlayer == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Betrayal_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> promissoryButtons = ButtonHelper.getForcedPNSendButtons(game, chosenPlayer, player);
        if (promissoryButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", you have no sendable promissory notes for _Betrayal_.");
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", choose which promissory note to send to "
                            + chosenPlayer.getColorIfCanSeeStats(player) + " for _Betrayal_.",
                    promissoryButtons);
        }

        List<Player> playersToSendRandomAc = new ArrayList<>();
        playersToSendRandomAc.add(chosenPlayer);
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || p2 == chosenPlayer) {
                continue;
            }
            boolean hasYourPromissoryInPlayArea = p2.getPromissoryNotesInPlayArea().stream()
                    .map(game::getPNOwner)
                    .anyMatch(player::equals);
            if (hasYourPromissoryInPlayArea) {
                playersToSendRandomAc.add(p2);
            }
        }

        List<String> playerNames = new ArrayList<>();
        for (Player p2 : playersToSendRandomAc) {
            playerNames.add(p2.getColorIfCanSeeStats(player));
            if (p2.getActionCards().isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        p2.getCardsInfoThread(),
                        p2.getRepresentationUnfogged() + ", you have no action cards to send for _Betrayal_.");
                continue;
            }

            List<Button> buttons = List.of(Buttons.green(
                    "resolveBetrayalCollect_" + player.getFaction(), "Send Random Action Card", CardEmojis.ActionCard));
            String msg = p2.getRepresentationUnfogged() + ", _Betrayal_ requires you to send 1 random action card to "
                    + (game.isFowMode() ? "another player." : player.getFactionEmojiOrColor() + ".");
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
            if (p2.isNpc()) {
                ActionCardHelper.sendRandomACPart2(event, game, p2, player);
            }
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " is resolving _Betrayal_. Random action card requests were sent to: "
                        + String.join(", ", playerNames) + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveBetrayalCollect_")
    public static void resolveBetrayalCollect(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player targetPlayer = game.getPlayerFromColorOrFaction(buttonID.replace("resolveBetrayalCollect_", ""));
        if (targetPlayer == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Betrayal_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (player.getActionCards().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", you have no action cards to send for _Betrayal_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        ActionCardHelper.sendRandomACPart2(event, game, player, targetPlayer);
        ButtonHelper.deleteMessage(event);
    }
}
