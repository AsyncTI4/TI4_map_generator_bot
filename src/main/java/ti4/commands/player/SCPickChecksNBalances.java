package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands.status.ListTurnOrder;
import ti4.generator.GenerateMap;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;
import java.util.stream.Collectors;

public class SCPickChecksNBalances {

    public static void secondHalfOfSCPick(GenericInteractionCreateEvent event, Player player, Game activeGame, int scPicked)
    {
        Boolean privateGame = FoWHelper.isPrivateGame(activeGame, event);
        boolean isFowPrivateGame = (privateGame != null && privateGame);
        String msg = "";
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        Collection<Player> activePlayers = activeGame.getPlayers().values().stream()
                .filter(Player::isRealPlayer)
                .collect(Collectors.toList());
        int maxSCsPerPlayer = activeGame.getSCList().size() / activePlayers.size();

        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getPlayerRepresentation(player, activeGame, event.getGuild(), true));
        sb.append(" Picked: ").append(Helper.getSCFrontRepresentation(activeGame, scPicked));

        boolean nextCorrectPing = false;
        Queue<Player> players = new ArrayDeque<>(activePlayers);
        while (players.iterator().hasNext()) {
            Player player_ = players.poll();
            if (player_ == null || !player_.isRealPlayer()) {
                continue;
            }
            int player_SCCount = player_.getSCs().size();
            if (nextCorrectPing && player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                msgExtra += Helper.getPlayerRepresentation(player_, activeGame, event.getGuild(), true) + " To Pick SC";
                activeGame.setCurrentPhase("strategy");
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

        //INFORM ALL PLAYER HAVE PICKED
        if (allPicked) {
            msgExtra += Helper.getGamePing(event, activeGame) + "\nAll players picked SC";

            LinkedHashMap<Integer, Integer> scTradeGoods = activeGame.getScTradeGoods();
            Set<Integer> scPickedList = new HashSet<>();
            for (Player player_ : activePlayers) {
                scPickedList.addAll(player_.getSCs());
            }

            //ADD A TG TO UNPICKED SC
            for (Integer scNumber : scTradeGoods.keySet()) {
                if (!scPickedList.contains(scNumber) && scNumber != 0) {
                    Integer tgCount = scTradeGoods.get(scNumber);
                    tgCount = tgCount == null ? 1 : tgCount + 1;
                    activeGame.setScTradeGood(scNumber, tgCount);
                }
            }

            for (int sc : scPickedList) {
                activeGame.setScTradeGood(sc, 0);
            }

            Player nextPlayer = null;
            int lowestSC = 100;
            for (Player player_ : activePlayers) {
                int playersLowestSC = player_.getLowestSC();
                String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player_, activeGame, Integer.toString(playersLowestSC));
                if (scNumberIfNaaluInPlay.startsWith("0/")) {
                    nextPlayer = player_; //no further processing, this player has the 0 token
                    break;
                }
                if (playersLowestSC < lowestSC) {
                    lowestSC = playersLowestSC;
                    nextPlayer = player_;
                }
            }

            //INFORM FIRST PLAYER IS UP FOR ACTION
            if (nextPlayer != null) {
                msgExtra += " " + Helper.getPlayerRepresentation(nextPlayer, activeGame) + " is up for an action";
                privatePlayer = nextPlayer;
                activeGame.updateActivePlayer(nextPlayer);
                activeGame.setCurrentPhase("action");
            }
        }
        msg = sb.toString();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

        //SEND EXTRA MESSAGE
        if (isFowPrivateGame ) {
            if (allPicked) {
                msgExtra = "# " + Helper.getPlayerRepresentation(privatePlayer, activeGame, event.getGuild(), true) + " UP NEXT";
            }
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, activeGame, event, msgExtra, fail, success);
            activeGame.updateActivePlayer(privatePlayer);
            
            if(!allPicked)
            {
                activeGame.setCurrentPhase("strategy");
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeGame, privatePlayer));
            }
            else{
                   
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), msgExtra + "\n Use Buttons to do turn.", ButtonHelper.getStartOfTurnButtons(privatePlayer, activeGame, false, event));
                    
                }

        } else {
            if (allPicked) {
                ListTurnOrder.turnOrder(event, activeGame);
            }
            if (!msgExtra.isEmpty()) {
                if(!allPicked && !activeGame.isHomeBrewSCMode())
                {
                    activeGame.updateActivePlayer(privatePlayer);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msgExtra+"\nUse Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeGame, privatePlayer));
                    activeGame.setCurrentPhase("strategy");
                }
                else{
                    if(allPicked)
                    {
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msgExtra);
                        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "\n Use Buttons to do turn.", ButtonHelper.getStartOfTurnButtons(privatePlayer, activeGame, false, event));

                    }
                    else
                    {
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msgExtra);
                    }
                }


            }
        }
    }
}
