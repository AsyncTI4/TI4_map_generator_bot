package ti4.buttons.handlers.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.strategycard.PlayStrategyCardService;

import java.util.List;

@UtilityClass
class StrategyCardButtonHandler {

    @ButtonHandler("deflectSC_")
    public static void deflectSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String scNum = buttonID.replace("deflectSC_", "");
        int sc = Integer.parseInt(scNum);
        game.setStoredValue("deflectedSC", scNum);
        String message = "Strategy Card " + sc + " has been deflected and will be resolved after all other strategy cards.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("increaseTGonSC_")
    public static void increaseTGonSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String scNum = buttonID.replace("increaseTGonSC_", "");
        int sc = Integer.parseInt(scNum);
        int currentTGs = game.getScTradeGoods().getOrDefault(sc, 0);
        game.setScTradeGood(sc, currentTGs + 1);
        String message = "Added 1 trade good to Strategy Card " + sc + ". It now has " + (currentTGs + 1) + " trade goods.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("assignSpeaker_")
    public static void assignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("assignSpeaker_", "");
        Player newSpeaker = game.getPlayerFromColorOrFaction(faction);
        if (newSpeaker != null) {
            game.setSpeakerUserID(newSpeaker.getUserID());
            String message = newSpeaker.getRepresentation() + " has been assigned as the Speaker.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)
    public static void sc3AssignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
        Player newSpeaker = game.getPlayerFromColorOrFaction(faction);
        if (newSpeaker != null) {
            game.setSpeakerUserID(newSpeaker.getUserID());
            String message = newSpeaker.getRepresentation() + " has been assigned as the Speaker via Politics strategy card.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = "requestAllFollow_", save = false)
    public static void requestAllFollow(ButtonInteractionEvent event, Game game) {
        if (game.getName().equalsIgnoreCase("fow273")) {
            event.getMessage().reply(event.getUser().getAsMention()
                + " has requested that everyone resolve this strategy card before play continues." +
                " Please do so as soon as you can. The active player should not take an action until this is done.")
                .queue();
        } else {
            event.getMessage().reply(game.getPing()
                + ", someone has requested that everyone resolve this strategy card before play continues." +
                " Please do so as soon as you can. The active player should not take an action until this is done.")
                .queue();
        }
    }

    // @ButtonHandler("strategicAction_")
    public static void strategicAction(
        ButtonInteractionEvent event, Player player, String buttonID, Game game,
        MessageChannel mainGameChannel
    ) {
        int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
        PlayStrategyCardService.playSC(event, scNum, game, mainGameChannel, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("primaryOfWarfare")
    public static void primaryOfWarfare(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "warfare");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
    }

    @ButtonHandler("leadershipExhaust")
    public static void leadershipExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", please choose the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons_leadership", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
    }
}