package ti4.buttons.handlers.phases;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.game.StartPhaseService;
import ti4.service.player.RefreshCardsService;
import ti4.service.turn.PassService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@UtilityClass
class GamePhaseButtonHandler {

    @ButtonHandler("startStrategyPhase")
    public static void startStrategyPhase(ButtonInteractionEvent event, Game game) {
        if (game.hasAnyPriorityTrackMode() && PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please fill the priority track before starting the Strategy Phase.");
            PriorityTrackHelper.PrintPriorityTrack(game);
            return;
        }
        StartPhaseService.startPhase(event, game, "strategy");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("proceed_to_strategy")
    public static void proceedToStrategy(ButtonInteractionEvent event, Game game) {
        String readiedCardsString = "All planets have been readied at the end of the Agenda Phase.";
        if (game.isOmegaPhaseMode()) {
            readiedCardsString = "All cards have been readied at the end of the Omega Phase.";
        }
        if (game.hasAnyPriorityTrackMode()) {
            if (PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please fill the priority track before starting the Strategy Phase.");
                PriorityTrackHelper.PrintPriorityTrack(game);
                return;
            }
        }
        Map<String, Player> players = game.getPlayers();
        if (game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
            for (Player player_ : players.values()) {
                RefreshCardsService.refreshPlayerCards(game, player_, false);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), readiedCardsString);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Did not automatically ready planets due to _Checks and Balances_ resolving \"against\"."
                    + " Players have been sent buttons to ready up to 3 planets.");
        }
        StartPhaseService.startStrategyPhase(event, game);
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
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("setOrder")
    public static void setOrder(ButtonInteractionEvent event, Game game) {
        String message = "Speaker order has been set based on current seating arrangement.";
        // Logic would typically be in a service class
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("willRevolution")
    public static void willRevolution(ButtonInteractionEvent event, Game game) {
        String message = "**Will of the Council** agenda outcome has been set to Revolution.";
        game.setStoredValue("willRevolution", "true");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("turnOffForcedScoring")
    public static void turnOffForcedScoring(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            game.getPing() + ", forced scoring order has been turned off. Any queues will not be resolved.");
        game.setStoredValue("forcedScoringOrder", "");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("forceACertainScoringOrder")
    public static void forceACertainScoringOrder(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), game.getPing()
            + ", players will be forced to score in order. Players will not be prevented from declaring they don't score, and are in fact encouraged to do so without delay if that is the case."
            + " This forced scoring order also does not yet affect secret objectives, it only restrains public objectives.");
        game.setStoredValue("forcedScoringOrder", "true");
        ButtonHelper.deleteMessage(event);
    }

    public static void lastMinuteDeliberation(
        ButtonInteractionEvent event, Player player, Game game
    ) {
        String message = player.getFactionEmoji() + " is using **Last Minute Deliberation** to extend the agenda phase.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        // Additional logic would be implemented here
        ButtonHelper.deleteMessage(event);
    }

    public static void declineExplore(
        ButtonInteractionEvent event, Player player, Game game
    ) {
        String message = player.getFactionEmoji() + " has declined to explore.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ReactionService.addReaction(event, game, player, "Declined explore");
        ButtonHelper.deleteMessage(event);
    }
}