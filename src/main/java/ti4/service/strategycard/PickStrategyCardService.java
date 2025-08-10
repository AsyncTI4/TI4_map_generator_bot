package ti4.service.strategycard;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.handlers.strategycard.PickStrategyCardButtonHandler;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.game.StartPhaseService;
import ti4.service.player.PlayerStatsService;

@UtilityClass
public class PickStrategyCardService {

    public static void secondHalfOfSCPick(GenericInteractionCreateEvent event, Player player, Game game, int scPicked) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);

        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        List<Player> activePlayers = getSCPickOrder(game);
        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer < 1) {
            maxSCsPerPlayer = 1;
        }
        if (!game.getStoredValue("exhaustedSC" + scPicked).isEmpty()) {
            game.setSCPlayed(scPicked, true);
        }

        boolean nextCorrectPing = false;
        Queue<Player> players = new ArrayDeque<>(activePlayers);
        while (players.iterator().hasNext()) {
            Player player_ = players.poll();
            if (player_ == null || !player_.isRealPlayer()) {
                continue;
            }
            int player_SCCount = player_.getSCs().size();
            if (nextCorrectPing && player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                msgExtra += player_.getRepresentationUnfogged() + " is up to pick their strategy card.";
                game.setPhaseOfGame("strategy");
                privatePlayer = player_;
                allPicked = false;
                break;
            }
            if (player_ == player) {
                nextCorrectPing = true;
            }
            if (player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                players.add(player_);
            }
        }

        // SEND EXTRA MESSAGE
        if (isFowPrivateGame) {
            String fail = "User for next faction not found. Report to ADMIN.";
            String success = "The next player has been notified.";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, game, event, msgExtra, fail, success);
            game.updateActivePlayer(privatePlayer);
            if (!allPicked) {
                game.setPhaseOfGame("strategy");
                game.updateActivePlayer(privatePlayer);
                boolean queuedPick = false;
                if (event instanceof ButtonInteractionEvent bevent) {
                    queuedPick = checkForQueuedSCPick(bevent, privatePlayer, game, msgExtra);
                }
                if (!queuedPick) {
                    checkForForcePickLastStratCard(event, privatePlayer, game, msgExtra);
                } else {
                    return;
                }
                // MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), "Use buttons to pick
                // your strategy card.", Helper.getRemainingSCButtons(game, privatePlayer));
            }
        } else {
            if (!allPicked) {
                game.updateActivePlayer(privatePlayer);
                game.setPhaseOfGame("strategy");
                boolean queuedPick = false;
                if (event instanceof ButtonInteractionEvent bevent) {
                    queuedPick = checkForQueuedSCPick(bevent, privatePlayer, game, msgExtra);
                }
                if (!queuedPick) {
                    checkForForcePickLastStratCard(event, privatePlayer, game, msgExtra);
                } else {
                    return;
                }
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msgExtra);
            }
        }
        if (allPicked) {
            StartPhaseService.startActionPhase(event, game);
        }
    }

    public static void checkForForcePickLastStratCard(
            GenericInteractionCreateEvent event, Player privatePlayer, Game game, String msgExtra) {
        List<Button> scButtons = Helper.getRemainingSCButtons(game, privatePlayer);
        if (scButtons.size()
                == 1) { // if there is only one strategy card left to pick (4p/8p games), force pick last strategy card
            MessageHelper.sendMessageToChannel(
                    privatePlayer.getCorrectChannel(),
                    privatePlayer.getRepresentation()
                            + ", you have only one available strategy card to pick. Bot will force pick for you.");
            int unpickedStrategyCard = 0;
            for (Integer sc : game.getSCList()) {
                if (sc <= 0) continue; // some older games have a 0 in the list of SCs
                boolean held = false;
                for (Player p : game.getPlayers().values()) {
                    if (p == null || p.getFaction() == null) {
                        continue;
                    }
                    if (p.getSCs() != null && p.getSCs().contains(sc)) {
                        held = true;
                        break;
                    }
                }
                if (held) continue;
                unpickedStrategyCard = sc;
            }
            PlayerStatsService.secondHalfOfPickSC(event, game, privatePlayer, unpickedStrategyCard);
            secondHalfOfSCPick(event, privatePlayer, game, unpickedStrategyCard);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(), msgExtra, scButtons);
        }
    }

    public static boolean checkForQueuedSCPick(
            ButtonInteractionEvent event, Player privatePlayer, Game game, String msgExtra) {
        Player player = privatePlayer;
        String alreadyQueued = game.getStoredValue(player.getFaction() + "scpickqueue");
        if (!alreadyQueued.isEmpty()) {
            int unpickedStrategyCard = 0;
            for (String scNum : alreadyQueued.split("_")) {
                game.setStoredValue(
                        player.getFaction() + "scpickqueue",
                        game.getStoredValue(player.getFaction() + "scpickqueue").replace(scNum + "_", ""));
                int sc = Integer.parseInt(scNum);
                boolean held = false;
                for (Player p : game.getRealPlayers()) {
                    if (p.getSCs() != null && p.getSCs().contains(sc)) {
                        held = true;
                        break;
                    }
                }
                if (held) continue;
                unpickedStrategyCard = sc;
                break;
            }
            if (unpickedStrategyCard == 0) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        "Tried to pick your queued strategy card, but they were all already taken.");
                return false;
            } else {
                MessageHelper.sendMessageToChannel(
                        privatePlayer.getCorrectChannel(),
                        privatePlayer.getRepresentation(false, false) + " had queued an strategy card pick.");
                return PickStrategyCardButtonHandler.scPick(event, game, player, "scPick_" + unpickedStrategyCard);
            }
        }
        return false;
    }

    public static List<Player> getSCPickOrder(Game game) {
        if (game.hasAnyPriorityTrackMode()) {
            List<Player> pickOrder = PriorityTrackHelper.GetPriorityTrack(game);
            if (game.getPriorityTrackMode() == PriorityTrackMode.AFTER_SPEAKER) {
                Player speaker = game.getSpeaker();
                if (speaker != null) {
                    pickOrder.remove(speaker);
                    pickOrder.add(0, speaker);
                }
            }
            return pickOrder;
        }

        List<Player> activePlayers = Helper.getSpeakerOrFullPriorityOrder(game);
        if (game.isReverseSpeakerOrder()
                || !game.getStoredValue("willRevolution").isEmpty()) {
            Collections.reverse(activePlayers);
        }
        return activePlayers;
    }

    public static int getSCPickOrderNumber(Game game, Player player) {
        List<Player> activePlayers = getSCPickOrder(game);
        int scPickOrder = 1;
        for (Player p : activePlayers) {
            if (p == null || p.getFaction() == null) {
                continue;
            }
            if (p.getFaction().equals(player.getFaction())) {
                break;
            }
            scPickOrder++;
        }
        return scPickOrder;
    }
}
