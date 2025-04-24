package ti4.service.turn;

import java.util.Collections;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.service.game.EndPhaseService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class EndTurnService {

    public static Player findNextUnpassedPlayer(Game game, Player currentPlayer) {
        List<Player> turnOrder = game.getActionPhaseTurnOrder();
        if (turnOrder.isEmpty()) {
            return null;
        }
        while (!turnOrder.getLast().equals(currentPlayer))
            Collections.rotate(turnOrder, 1);
        for (Player p : turnOrder) {
            if (!p.isPassed() && !p.isEliminated()) {
                return p;
            }
        }
        return null;
    }

    public static void endTurnAndUpdateMap(GenericInteractionCreateEvent event, Game game, Player player) {
        pingNextPlayer(event, game, player);
        CommanderUnlockCheckService.checkPlayer(player, "naaz");
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event, "End of Turn " + player.getInRoundTurnCount() + ", Round " + game.getRound() + " for " + player.getRepresentationNoPing() + ".");
        }
    }

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game game, Player mainPlayer) {
        pingNextPlayer(event, game, mainPlayer, false);
    }

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game game, Player mainPlayer, boolean justPassed) {
        game.setStoredValue("lawsDisabled", "no");
        game.removeStoredValue("endTurnWhenSCFinished");
        game.removeStoredValue("fleetLogWhenSCFinished");
        CommanderUnlockCheckService.checkPlayer(mainPlayer, "sol", "hacan");
        for (Player player : game.getRealPlayers()) {
            for (Player player_ : game.getRealPlayers()) {
                if (player_ == player) {
                    continue;
                }
                String key = player.getFaction() + "whisperHistoryTo" + player_.getFaction();
                if (!game.getStoredValue(key).isEmpty()) {
                    game.setStoredValue(key, "");
                }
            }
        }
        game.setStoredValue("mahactHeroTarget", "");
        game.setActiveSystem("");
        game.setStoredValue("possiblyUsedRift", "");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _\n"
                + "**End of Turn " + mainPlayer.getInRoundTurnCount() + ", Round " + game.getRound() + " for** " + mainPlayer.getRepresentation());
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), mainPlayer.getRepresentation(true, false) + " ended turn.");
        }

        MessageChannel gameChannel = game.getMainGameChannel() == null ? event.getMessageChannel() : game.getMainGameChannel();

        //MAKE ALL NON-REAL PLAYERS PASSED
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                player.setPassed(true);
            }
        }

        if (game.getPlayers().values().stream().allMatch(Player::isPassed)) {
            if (mainPlayer.getSecretsUnscored().containsKey("pe")) {
                MessageHelper.sendMessageToChannel(mainPlayer.getCardsInfoThread(),
                    "You were the last player to pass, and so you can score _Prove Endurance_.");
            }
            EndPhaseService.EndActionPhase(event, game, gameChannel);
            game.updateActivePlayer(null);
            ButtonHelperAgents.checkForEdynAgentPreset(game, mainPlayer, mainPlayer, event);
            ButtonHelperAgents.checkForEdynAgentActive(game, event);
            return;
        }

        Player nextPlayer = findNextUnpassedPlayer(game, mainPlayer);
        if (!game.isFowMode()) {
            GameMessageManager
                .remove(game.getName(), GameMessageType.TURN)
                .ifPresent(messageId -> game.getMainGameChannel().deleteMessageById(messageId).queue());
        }
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game);
        if (isFowPrivateGame) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, mainPlayer, "ended turn");
        }
        ButtonHelper.checkFleetInEveryTile(mainPlayer, game, event);
        if (mainPlayer != nextPlayer) {
            ButtonHelper.checkForPrePassing(game, mainPlayer);
        }
        CommanderUnlockCheckService.checkPlayer(nextPlayer, "sol");
        if (justPassed) {
            if (!ButtonHelperAgents.checkForEdynAgentPreset(game, mainPlayer, nextPlayer, event)) {
                StartTurnService.turnStart(event, game, nextPlayer);
            }
        } else {
            if (!ButtonHelperAgents.checkForEdynAgentActive(game, event)) {
                StartTurnService.turnStart(event, game, nextPlayer);
            }
        }

    }
}
