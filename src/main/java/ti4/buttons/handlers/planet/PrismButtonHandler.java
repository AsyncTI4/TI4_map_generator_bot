package ti4.buttons.handlers.planet;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.TransactionHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.turn.StartTurnService;

@UtilityClass
class PrismButtonHandler {

    @ButtonHandler("newPrism@")
    public static void newPrismPart2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String techOut = buttonID.split("@")[1];
        player.purgeTech(techOut);
        TechnologyModel techM1 = Mapper.getTech(techOut);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " purged the technology _" + techM1.getName() + "_.");
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), player.getRepresentation()
            + ", use the button to get a technology that also has " + techM1.getRequirements().orElse("").length() + " prerequisites.", Buttons.GET_A_FREE_TECH);
        event.getMessage().delete().queue();
        String message2 = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, systemButtons);
    }

    @ButtonHandler("prismStep2_")
    public static void resolvePrismStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();

        buttons.add(Buttons.gray("prismStep3_" + player.getFaction() + "_AC", "Send Action Card"));
        buttons.add(Buttons.gray("prismStep3_" + player.getFaction() + "_PN", "Send Promissory Notes"));

        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " chose " + p2.getFactionEmojiOrColor() + " as the target of the Prism ability. The target has been sent buttons to resolve.");
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
            p2.getRepresentationUnfogged() + ", you have had the Prism ability hit you. Please tell the bot if you wish to send an action card or a promissory note.", buttons);
    }

    @ButtonHandler("prismStep3_")
    public static void resolvePrismStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons;
        String pnOrAC = buttonID.split("_")[2];
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " chose to send a " + pnOrAC + ".");
        if ("pn".equalsIgnoreCase(pnOrAC)) {
            buttons = ButtonHelper.getForcedPNSendButtons(game, p2, player);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged() + ", choose which promissory note to send.", buttons);
        } else {
            String buttonID2 = "transact_ACs_" + p2.getFaction();
            TransactionHelper.resolveSpecificTransButtonsOld(game, player, buttonID2, event);
        }
    }
}
