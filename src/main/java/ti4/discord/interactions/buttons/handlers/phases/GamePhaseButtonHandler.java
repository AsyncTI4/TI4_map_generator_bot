package ti4.discord.interactions.buttons.handlers.phases;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Helper;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.message.MessageHelper;
import ti4.service.StatusCleanupService;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.button.ReactionService;
import ti4.service.game.StartPhaseService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.turn.PassService;
import ti4.service.turn.StartTurnService;

@UtilityClass
class GamePhaseButtonHandler {

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

    @ButtonHandler("finishComponentAction_")
    public static void finishComponentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), event.getMessage().getContentRaw());
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("passForRound")
    public static void passForRound(ButtonInteractionEvent event, Player player, Game game) {
        PassService.passPlayerForRound(event, game, player, false);
        ButtonHelper.deleteMessage(event);
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

    @ButtonHandler("passingAbilities")
    private static void passingAbilities(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "Use these buttons to do an ability when you pass.";
        List<Button> buttons = ButtonHelper.getPassingAbilities(player, game);
        buttons.addFirst(Buttons.red(player.finChecker() + "passForRound", "Pass"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("run_status_cleanup")
    public static void runStatusCleanup(ButtonInteractionEvent event, Game game, Player player) {
        StatusCleanupService.runStatusCleanup(game);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        ReactionService.addReaction(
                event, game, player, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
    }

    @ButtonHandler("startOfGameObjReveal")
    public static void startOfGameObjReveal(ButtonInteractionEvent event, Game game) {
        for (Player p : game.getRealPlayers()) {
            if (p.getSecrets().size() > 1 && !game.isExtraSecretMode()) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Please ensure everyone has discarded secret objectives before hitting this button. ");
                return;
            }
        }

        Player speaker = null;
        if (game.getPlayer(game.getSpeakerUserID()) != null) {
            speaker = game.getPlayers().get(game.getSpeakerUserID());
        }
        if (speaker == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Please assign speaker before hitting this button.");
            ButtonHelper.offerSpeakerButtons(game);
            return;
        }
        if (game.hasAnyPriorityTrackMode()
                && PriorityTrackHelper.getPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            PriorityTrackHelper.CreateDefaultPriorityTrack(game);
            if (PriorityTrackHelper.getPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Failed to fill the Priority Track with the default seating order. Use `/omegaphase assign_player_priority` to fill the track before proceeding.");
                PriorityTrackHelper.PrintPriorityTrack(game);
                return;
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Set up the Priority Track in the default seating order.");
            PriorityTrackHelper.PrintPriorityTrack(game);
        }
        if (!game.getStoredValue("revealedFlop" + game.getRound()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    "The bot thinks that public objectives were already revealed. Try doing `/status reveal` if this was a mistake.");
            return;
        } else {
            game.setStoredValue("revealedFlop" + game.getRound(), "Yes");
        }

        if (game.isCivilizedSocietyMode()) {
            RevealPublicObjectiveService.revealAllObjectives(game);
        } else {
            RevealPublicObjectiveService.revealTwoStage1(game);
        }

        if (game.isTwilightsFallMode()
                && !game.getStoredValue("needsInauguralSplice").isEmpty()) {
            game.removeStoredValue("needsInauguralSplice");
            ButtonHelperTwilightsFall.startInauguralSplice(game);
        } else {
            startOfGameStrategyPhase(event, game);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startOfGameStrategyPhase")
    public static void startOfGameStrategyPhase(ButtonInteractionEvent event, Game game) {
        StartPhaseService.startStrategyPhase(event, game);
        PlayerPreferenceHelper.offerSetAutoPassOnSaboButtons(game, null);
        ButtonHelper.deleteMessage(event);
        // Reduce file size by clearing draft info
        game.clearAllDraftInfo();
    }

    @ButtonHandler("setOrder")
    public static void setOrder(ButtonInteractionEvent event, Game game) {
        Helper.setOrder(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("temporaryPingDisable")
    public static void temporaryPingDisable(ButtonInteractionEvent event, Game game) {
        game.setTemporaryPingDisable(true);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Disabled autopings for this turn.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("non_sc_draw_so")
    public static void nonSCDrawSO(ButtonInteractionEvent event, Player player, Game game) {
        String message = "drew a secret objective.";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message += ". Drew a second secret objective due to **Plausible Deniability**.";
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("genericReact")
    public static void genericReact(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "Turned down window" : null;
        ReactionService.addReaction(event, game, player, message);
    }
}
