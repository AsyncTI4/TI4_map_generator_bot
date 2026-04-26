package ti4.discord.interactions.buttons.handlers.phases;

import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.message.MessageHelper;
import ti4.service.game.StartPhaseService;
import ti4.service.player.RefreshCardsService;

@UtilityClass
class StrategyPhaseButtonHandler {

    @ButtonHandler("startStrategyPhase")
    public static void startStrategyPhase(ButtonInteractionEvent event, Game game) {
        if (game.hasAnyPriorityTrackMode()
                && PriorityTrackHelper.getPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Please fill the priority track before starting the Strategy Phase.");
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
            if (PriorityTrackHelper.getPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Please fill the priority track before starting the Strategy Phase.");
                PriorityTrackHelper.PrintPriorityTrack(game);
                return;
            }
        }
        Map<String, Player> players = game.getPlayers();
        if (game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
            if (!game.isTwilightsFallMode()) {
                for (Player player_ : players.values()) {
                    RefreshCardsService.refreshPlayerCards(game, player_, false);
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), readiedCardsString);
            }
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Did not automatically ready planets due to _Checks and Balances_ resolving \"against\"."
                            + " Players have been sent buttons to ready up to 3 planets.");
        }
        StartPhaseService.startStrategyPhase(event, game);
        ButtonHelper.deleteMessage(event);
    }
}
