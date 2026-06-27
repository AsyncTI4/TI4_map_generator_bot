package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
class HiddenInitiativesAcd2ButtonHandler {

    @ButtonHandler("resolveHiddenInitiatives")
    public static void resolveHiddenInitiatives(Player player, Game game, ButtonInteractionEvent event) {
        List<String> peekedSecrets = game.peekAtSecrets(2);
        if (peekedSecrets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", the secret objective deck is empty; _Hidden Initiatives_ cannot be resolved.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        List<MessageEmbed> embeds = peekedSecrets.stream()
                .map(id -> Mapper.getSecretObjective(id).getRepresentationEmbed(true))
                .toList();
        List<Button> buttons = new ArrayList<>();
        for (String soId : peekedSecrets) {
            String soName = Mapper.getSecretObjective(soId).getName();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "resolveHiddenInitiativesStep2_" + soId,
                    "Take \"" + soName + "\"",
                    CardEmojis.SecretObjective));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "resolveHiddenInitiativesNoSwap", "No Swap"));

        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player,
                player.getRepresentationUnfogged() + ", these are the top " + peekedSecrets.size()
                        + " secret objective(s) from the deck. Choose one to replace an unscored secret objective, or decline.",
                embeds);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose a secret objective to take, or decline.",
                buttons);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getFactionEmojiOrColor() + " is resolving _Hidden Initiatives_.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveHiddenInitiativesStep2_")
    public static void resolveHiddenInitiativesStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String deckSOId = buttonID.replace("resolveHiddenInitiativesStep2_", "");
        Map<String, Integer> unscoredSOs = player.getSecretsUnscored();
        if (unscoredSOs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", you have no unscored secret objectives to replace. The deck will be shuffled.");
            Collections.shuffle(game.getSecretObjectives());
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : unscoredSOs.entrySet()) {
            String soName = Mapper.getSecretObjective(entry.getKey()).getName();
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "resolveHiddenInitiativesStep3_" + deckSOId + "_"
                            + entry.getValue(),
                    "Replace \"" + soName + "\"",
                    CardEmojis.SecretObjective));
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + "resolveHiddenInitiativesNoSwap", "No Swap"));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", choose which of your unscored secret objectives to replace with \""
                        + Mapper.getSecretObjective(deckSOId).getName() + "\".",
                buttons);
    }

    @ButtonHandler("resolveHiddenInitiativesStep3_")
    public static void resolveHiddenInitiativesStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("resolveHiddenInitiativesStep3_", "");
        String[] parts = payload.split("_", 2);
        if (parts.length < 2) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Hidden Initiatives_: malformed button.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String deckSOId = parts[0];
        int playerSONum;
        try {
            playerSONum = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Hidden Initiatives_: invalid identifier.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        // Find the string ID of the player's SO by its numeric identifier.
        String playerSOId = null;
        for (Map.Entry<String, Integer> entry : player.getSecretsUnscored().entrySet()) {
            if (entry.getValue().equals(playerSONum)) {
                playerSOId = entry.getKey();
                break;
            }
        }
        if (playerSOId == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", could not find that secret objective in your hand. The deck will be shuffled.");
            Collections.shuffle(game.getSecretObjectives());
            ButtonHelper.deleteMessage(event);
            return;
        }

        String replacedSOName = Mapper.getSecretObjective(playerSOId).getName();
        String takenSOName = Mapper.getSecretObjective(deckSOId).getName();

        // Return the player's SO to the deck, then draw the chosen deck SO for the player.
        player.removeSecret(playerSONum);
        game.addSOToGame(playerSOId);
        game.drawSpecificSecretObjective(deckSOId, player.getUserID());
        Collections.shuffle(game.getSecretObjectives());

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getFactionEmojiOrColor()
                        + " resolved _Hidden Initiatives_ and swapped a secret objective. The secret objective deck has been shuffled.");
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " replaced \"" + replacedSOName + "\" with \"" + takenSOName
                        + "\" via _Hidden Initiatives_.");
    }

    @ButtonHandler("resolveHiddenInitiativesNoSwap")
    public static void resolveHiddenInitiativesNoSwap(Player player, Game game, ButtonInteractionEvent event) {
        Collections.shuffle(game.getSecretObjectives());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getFactionEmojiOrColor()
                        + " resolved _Hidden Initiatives_ without swapping. The secret objective deck has been shuffled.");
    }
}
