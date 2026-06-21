package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
class ReengineerAcd2ButtonHandler {

    @ButtonHandler("resolveReengineer")
    public static void resolveReengineer(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        String lastDiscard = lastDiscard(game);
        if (lastDiscard != null) {
            String name = Mapper.getActionCard(lastDiscard).getName();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "reengineerTakeDiscard",
                    "Take _" + name + "_ from the discard",
                    CardEmojis.getACEmoji(game)));
        }
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + "reengineerDraw2", "Draw 2 Action Cards", CardEmojis.getACEmoji(game)));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", choose how to resolve _Reengineer_. Afterwards you will discard 1 action card.",
                buttons);
    }

    @ButtonHandler("reengineerTakeDiscard")
    public static void resolveReengineerTakeDiscard(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        String lastDiscard = lastDiscard(game);
        if (lastDiscard == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "There is no action card in the discard pile for _Reengineer_.");
        } else {
            String name = Mapper.getActionCard(lastDiscard).getName();
            player.setActionCard(lastDiscard);
            game.getDiscardActionCards().remove(lastDiscard);
            ActionCardHelper.sendActionCardInfo(game, player);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " took _" + name + "_ from the discard pile for _Reengineer_.");
        }
        sendReengineerDiscardStep(player);
    }

    @ButtonHandler("reengineerDraw2")
    public static void resolveReengineerDraw2(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        ActionCardHelper.drawActionCards(player, 2);
        sendReengineerDiscardStep(player);
    }

    private static void sendReengineerDiscardStep(Player player) {
        List<Button> buttons = ActionCardHelper.getDiscardActionCardButtons(player, false);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " has no action cards to discard for _Reengineer_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", discard 1 action card for _Reengineer_.",
                buttons);
    }

    private static String lastDiscard(Game game) {
        Map<String, Integer> discard = game.getDiscardActionCards();
        if (discard == null || discard.isEmpty()) {
            return null;
        }
        String last = null;
        for (String acID : discard.keySet()) {
            last = acID;
        }
        return last;
    }
}
