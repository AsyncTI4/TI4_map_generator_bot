package ti4.buttons.handlers.other;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.service.emoji.PlanetEmojis;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.tactical.TacticalActionService;

import java.util.List;

@UtilityClass
class ActionButtonHandler {

    @ButtonHandler("doActivation_")
    public static void doActivation(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("doActivation_", "");
        ButtonHelper.resolveOnActivationEnemyAbilities(game, game.getTileByPosition(pos), player, false, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ring_")
    public static void ring(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> ringButtons = ButtonHelper.getTileInARing(player, game, buttonID);
        String num = buttonID.replace("ring_", "");
        String message;
        if (!"corners".equalsIgnoreCase(num)) {
            int ring = Integer.parseInt(num.charAt(0) + "");
            if (ring > 4 && !num.contains("left") && !num.contains("right")) {
                message = "That ring is very large. Specify if your tile is on the left or right side of the map (center will be counted in both).";
            } else {
                message = "Please choose the system that you wish to activate.";
            }
        } else {
            message = "Please choose the system that you wish to activate.";
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("componentAction")
    public static void componentAction(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ComponentActionHelper.getAllPossibleCompButtons(game, player, event);
        String message = player.getRepresentationUnfogged() + ", use buttons to do a component action.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("corefactoryAction")
    public static void coreFactoryAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation() + " is using their _Core Factory_ component action.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);

        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_dontskip");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            "Choose a planet to place a mech on", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("finishComponentAction_")
    public static void finishComponentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getFactionEmoji() + " finished their component action.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        game.setComponentAction(false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doAnotherAction")
    public static void doAnotherAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getFactionEmoji() + " wants to do another action.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        game.setComponentAction(false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("finishComponentAction")
    public static void finishComponentActionAlt(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getFactionEmoji() + " finished their component action.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        game.setComponentAction(false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("sabotage_")
    public static void sabotage(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String acAlias = buttonID.replace("sabotage_", "");
        String acName = ActionCardHelper.getActionCardName(acAlias);
        String message = player.getFactionEmoji() + " played **Sabotage** on " + acName;

        ActionCardHelper.resolveActionCard(acAlias, event, game, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("no_sabotage")
    public static void noSabotage(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "No Sabotage" : null;
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("useTA_")
    public static void useTA(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String ta = buttonID.replace("useTA_", "") + "_ta";
        PromissoryNoteHelper.resolvePNPlay(ta, player, game, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("toldarPN")
    public static void toldarPN(ButtonInteractionEvent event, Player player) {
        String message = player.getFactionEmoji() + " used their Toldar promissory note ability.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("useLawsOrder")
    public static void useLawsOrder(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getFactionEmoji() + " is using **Law's Order** to ready an exhausted planet.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            "Choose a planet to ready", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startYinSpinner")
    public static void startYinSpinner(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getFactionEmoji() + " is starting the Yin Spinner ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        // Additional Yin Spinner logic would go here
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeHolderOfConInSystem_")
    public static void placeHolderOfConInSystem(
        GenericInteractionCreateEvent event, Game game, Player player,
        String buttonID
    ) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String message = player.getFactionEmoji() + " placed a Consolidation of Conquest token in " +
            tile.getRepresentationForButtons(game, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        // Logic to place the token would go here
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("rollIxthian")
    public static void rollIxthian(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (game.getSpeakerUserID().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
            AgendaHelper.rollIxthian(game, true);
        } else {
            Button ixthianButton = Buttons.green("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact",
                PlanetEmojis.Mecatol);
            String msg = "The speaker should roll for _Ixthain Artifact_. Click this button to roll anyway!";
            MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg, ixthianButton);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void declineExplore(
        ButtonInteractionEvent event, Player player, Game game,
        MessageChannel mainGameChannel
    ) {
        String message = player.getFactionEmoji() + " has declined to explore.";
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ReactionService.addReaction(event, game, player, "Declined explore");
        ButtonHelper.deleteMessage(event);
    }
}