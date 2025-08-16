package ti4.buttons.handlers.agenda;

import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.handlers.strategycard.PickStrategyCardButtonHandler;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.game.StartPhaseService;
import ti4.service.player.PlayerStatsService;

@UtilityClass
class ChecksAndBalancesButtonHandler {

    @ButtonHandler("checksNBalancesPt2_")
    public static void resolvePt2ChecksNBalances(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        int scPicked = Integer.parseInt(buttonID.split("_")[1]);
        String factionPicked = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(factionPicked);

        PlayerStatsService.secondHalfOfPickSC(event, game, p2, scPicked);

        String recipientMessage = p2.getRepresentationUnfogged() + " was given " + Helper.getSCName(scPicked, game)
                + (!game.isFowMode() ? " by " + player.getFactionEmoji() : "") + ".";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), recipientMessage);

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    p2.getRepresentationNoPing() + " was given " + Helper.getSCName(scPicked, game) + ".");
        }
        event.getMessage().delete().queue();
        List<Button> buttons = PickStrategyCardButtonHandler.getPlayerOptionsForChecksNBalances(player, game, scPicked);
        if (buttons.isEmpty()) {
            StartPhaseService.startActionPhase(event, game);
            game.setStoredValue("willRevolution", "");
        } else {
            boolean foundPlayer = false;
            Player privatePlayer = null;
            List<Player> players = game.getRealPlayers();
            if (game.isReverseSpeakerOrder()
                    || !game.getStoredValue("willRevolution").isEmpty()) {
                Collections.reverse(players);
            }
            for (Player p3 : players) {
                if (p3.getFaction().equalsIgnoreCase(game.getStoredValue("politicalStabilityFaction"))) {
                    continue;
                }
                if (foundPlayer) {
                    privatePlayer = p3;
                    foundPlayer = false;
                }
                if (p3 == player) {
                    foundPlayer = true;
                }
            }
            if (privatePlayer == null) {
                privatePlayer = game.getRealPlayers().getFirst();
            }
            game.setPhaseOfGame("strategy");
            game.updateActivePlayer(privatePlayer);
            MessageHelper.sendMessageToChannelWithButtons(
                    privatePlayer.getCorrectChannel(),
                    privatePlayer.getRepresentationUnfogged()
                            + ", please choose which strategy card you wish to give someone else.",
                    Helper.getRemainingSCButtons(game, privatePlayer));
        }
    }
}
