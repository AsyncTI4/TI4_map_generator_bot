package ti4.buttons.handlers.phases;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.button.ReactionService;
import ti4.service.turn.PassService;
import ti4.service.turn.StartTurnService;

@UtilityClass
class ActionPhaseButtonHandler {

    @ButtonHandler("doActivation_")
    public static void doActivation(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("doActivation_", "");
        ButtonHelper.resolveOnActivationEnemyAbilities(game, game.getTileByPosition(pos), player, false, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericReact")
    public static void genericReact(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "Turned down window" : null;
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("finishComponentAction_")
    public static void finishComponentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), event.getMessage().getContentRaw());
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("componentAction")
    public static void componentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Please choose what kind of component action you wish to do.";
        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            message += "\n-# You have been _Political Censure_'d, and thus cannot play action cards.";
        }
        List<Button> systemButtons = ComponentActionHelper.getAllPossibleCompButtons(game, player, event);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, systemButtons);
    }

    @ButtonHandler("temporaryPingDisable")
    public static void temporaryPingDisable(ButtonInteractionEvent event, Game game) {
        game.setTemporaryPingDisable(true);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Disabled autopings for this turn.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("passingAbilities")
    private static void passingAbilities(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "Use these buttons to do an ability when you pass.";
        List<Button> buttons = ButtonHelper.getPassingAbilities(player, game);
        buttons.addFirst(Buttons.red(player.finChecker() + "passForRound", "Pass"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("endOfTurnAbilities")
    public static void endOfTurnAbilities(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "Use buttons to do an end of turn ability";
        List<Button> buttons = ButtonHelper.getEndOfTurnAbilities(player, game);
        ButtonHelper.deleteMessage(event);
        if (!buttons.isEmpty()) {
            buttons.addFirst(Buttons.red(player.finChecker() + "turnEnd", "End Turn"));
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), msg, buttons);
        } else {
            TurnEndButtonHandler.turnEnd(event, game, player);
        }
    }

    @ButtonHandler("passForRound")
    public static void passForRound(ButtonInteractionEvent event, Player player, Game game) {
        PassService.passPlayerForRound(event, game, player, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("no_sabotage")
    public static void noSabotage(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "No Sabotage" : null;
        ReactionService.addReaction(event, game, player, message);
    }
}
