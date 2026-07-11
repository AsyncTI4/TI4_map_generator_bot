package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
class ClassifiedRiderAcd2ButtonHandler {

    @ButtonHandler("resolveClassifiedRider")
    public static void resolveClassifiedRider(Player player, Game game, ButtonInteractionEvent event) {
        List<String> peekedSecrets = game.peekAtSecrets(3);
        if (peekedSecrets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + ", the secret objective deck is empty; _Classified Rider_ cannot be resolved.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<MessageEmbed> embeds = peekedSecrets.stream()
                .map(id -> Mapper.getSecretObjective(id).getRepresentationEmbed(true))
                .toList();
        List<Button> buttons = new ArrayList<>();
        for (String soId : peekedSecrets) {
            String soName = Mapper.getSecretObjective(soId).getName();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "resolveClassifiedRiderStep2_" + soId,
                    "Take \"" + soName + "\"",
                    CardEmojis.SecretObjective));
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player,
                player.getRepresentationUnfogged()
                        + ", these are the top " + peekedSecrets.size()
                        + " secret objective(s) from the deck. Choose one to draw for _Classified Rider_.",
                embeds);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose a secret objective to draw for _Classified Rider_.",
                buttons);
    }

    @ButtonHandler("resolveClassifiedRiderStep2_")
    public static void resolveClassifiedRiderStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String soId = buttonID.replace("resolveClassifiedRiderStep2_", "");
        if (!game.getSecretObjectives().contains(soId)) {
            Collections.shuffle(game.getSecretObjectives());
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", that secret objective is no longer available. The secret objective deck has been shuffled.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.drawSpecificSecretObjective(soId, player.getUserID());
        Collections.shuffle(game.getSecretObjectives());
        ButtonHelper.deleteMessage(event);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getFactionEmojiOrColor()
                        + " resolved _Classified Rider_ and drew a secret objective. The secret objective deck has been shuffled.");
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " drew \""
                        + Mapper.getSecretObjective(soId).getName() + "\" with _Classified Rider_.");
    }
}
