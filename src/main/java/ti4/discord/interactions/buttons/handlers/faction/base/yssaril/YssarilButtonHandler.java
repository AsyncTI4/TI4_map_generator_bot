package ti4.discord.interactions.buttons.handlers.faction.base.yssaril;

import java.util.ArrayList;
import java.util.List;
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
import ti4.model.ActionCardModel;
import ti4.service.emoji.CardEmojis;

@UtilityClass
class YssarilButtonHandler {

    @ButtonHandler("yssarilHeroInitialOffering_")
    public static void yssarilHeroInitialOffering(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String buttonLabel = event.getButton().getLabel();
        List<Button> acButtons = new ArrayList<>();
        buttonID = buttonID.replace("yssarilHeroInitialOffering_", "");
        String acID = buttonID.split("_")[0];
        String yssarilFaction = buttonID.split("_")[1];
        Player yssaril = game.getPlayerFromColorOrFaction(yssarilFaction);
        if (yssaril == null) {
            return;
        }

        String offerName = player.getRepresentationNoPing();
        if (game.isFowMode()) {
            offerName = player.getColor();
        }
        ButtonHelper.deleteMessage(event);
        acButtons.add(Buttons.green(
                "takeAC_" + acID + "_" + player.getFaction(), "Take " + buttonLabel, CardEmojis.getACEmoji(game)));
        acButtons.add(Buttons.red(
                "yssarilHeroRejection_" + player.getFaction(), "Reject " + buttonLabel + " and Force Discard"));
        String message = yssaril.getRepresentationUnfogged() + " " + offerName + " has offered you the action card "
                + buttonLabel
                + " for Kyver, Blade and Key, the Yssaril hero. Use buttons to accept it, or to reject it and force them to discard 3 random action cards.";
        MessageHelper.sendMessageToChannelWithButtons(yssaril.getCardsInfoThread(), message, acButtons);
        String acStringID = null;
        for (String acStrId : player.getActionCards().keySet()) {
            if ((player.getActionCards().get(acStrId) + "").equalsIgnoreCase(acID)) {
                acStringID = acStrId;
            }
        }
        if (acStringID == null) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Unable to find AC with id " + acID + " for " + player.getUserName());
            return;
        }
        ActionCardModel ac = Mapper.getActionCard(acStringID);
        MessageHelper.sendMessageToChannelWithEmbed(
                yssaril.getCardsInfoThread(),
                "For your reference, the text of the action cards offered reads as:",
                ac.getRepresentationEmbed(false, false, game));
    }

    @ButtonHandler("yssarilMinisterOfPolicy")
    public static void yssarilMinisterOfPolicy(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " is drawing their _Minister of Policy_ action card.");
        ActionCardHelper.drawActionCards(player, 1);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("yssarilHeroRejection_")
    public static void yssarilHeroRejection(Game game, ButtonInteractionEvent event, String buttonID) {
        String playerFaction = buttonID.replace("yssarilHeroRejection_", "");
        Player notYssaril = game.getPlayerFromColorOrFaction(playerFaction);
        if (notYssaril != null) {
            String message = notYssaril.getRepresentationUnfogged()
                    + " Kyver, Blade and Key, the Yssaril hero, has rejected your offering and so is forcing you to discard 3 random action cards. The action cards have been automatically discarded.";
            MessageHelper.sendMessageToChannel(notYssaril.getCardsInfoThread(), message);
            ActionCardHelper.discardRandomAC(event, game, notYssaril, 3);
            ButtonHelper.deleteMessage(event);
        }
    }
}
