package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.status.ListTurnOrder;
import ti4.generator.GenerateMap;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;
import java.util.stream.Collectors;

public class SCPick extends PlayerSubcommandData {
    public SCPick() {
        super(Constants.SC_PICK, "Pick SC");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card #").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC2, "2nd choice"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC3, "3rd"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC4, "4th"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC5, "5th"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC6, "6th"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR,"Faction or Color for which you set stats").setAutoComplete(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
		Game activeGame = getActiveGame();
		Player player = activeGame.getPlayer(getUser().getId());
		player = Helper.getGamePlayer(activeGame, player, event, null);
		player = Helper.getPlayer(activeGame, player, event);
		if (player == null) {
			sendMessage("Player could not be found");
			return;
		}

        Collection<Player> activePlayers = activeGame.getPlayers().values().stream()
                .filter(Player::isRealPlayer)
                .toList();
        if (activePlayers.size() == 0) {
            sendMessage("No active players found");
            return;
        }

        int maxSCsPerPlayer = activeGame.getSCList().size() / activePlayers.size();
        if (maxSCsPerPlayer == 0) maxSCsPerPlayer = 1;

        int playerSCCount = player.getSCs().size();
        if (playerSCCount >= maxSCsPerPlayer) {
            sendMessage("Player can not pick another SC. Max SC per player for this game is " + maxSCsPerPlayer);
            return;
        }

        OptionMapping option = event.getOption(Constants.STRATEGY_CARD);
        int scPicked = option.getAsInt();

        Stats stats = new Stats();
        boolean pickSuccessful = stats.pickSC(event, activeGame, player, option);
        LinkedHashSet<Integer> playerSCs = player.getSCs();
        if (!pickSuccessful) {
            if (activeGame.isFoWMode()) {
                String[] scs = {Constants.SC2, Constants.SC3, Constants.SC4, Constants.SC5, Constants.SC6};
                int c = 0;
                while(playerSCs.isEmpty() && c < 5 && !pickSuccessful){
                    if (event.getOption(scs[c]) != null)
                    {
                        pickSuccessful = stats.pickSC(event, activeGame, player, event.getOption(scs[c]));
                    }
                    playerSCs = player.getSCs();
                    c++;
                }
            }
            if(!pickSuccessful)
            {
                return;
            }
        }
        //ONLY DEAL WITH EXTRA PICKS IF IN FoW
        if (playerSCs.isEmpty()) {
            sendMessage("No SC picked.");
            return;
        }
        secondHalfOfSCPick(event, player, activeGame, scPicked);
    }

    public void secondHalfOfSCPick(GenericInteractionCreateEvent event, Player player, Game activeGame, int scPicked)
    {
        Boolean privateGame = FoWHelper.isPrivateGame(activeGame, event);
        boolean isFowPrivateGame = (privateGame != null && privateGame);
        String msg;
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        List<Player> activePlayers = activeGame.getPlayers().values().stream()
                .filter(Player::isRealPlayer)
                .collect(Collectors.toList());
        if(activeGame.isReverseSpeakerOrder()) {
            Collections.reverse(activePlayers);
        }
        int maxSCsPerPlayer = activeGame.getSCList().size() / activePlayers.size();
        if(maxSCsPerPlayer < 1){
            maxSCsPerPlayer = 1;
        }

        String sb = Helper.getPlayerRepresentation(player, activeGame, event.getGuild(), true) +
            " Picked: " + Helper.getSCFrontRepresentation(activeGame, scPicked);

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
                ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, activeGame);
                if(activeGame.isFoWMode()){
                    FoWHelper.pingAllPlayersWithFullStats(activeGame, event, nextPlayer, "started turn");
                }
                
                activeGame.setCurrentPhase("action");
            }
        }
        msg = sb;
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
                if(player.getStasisInfantry() > 0){
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), "Use buttons to revive infantry. You have "+player.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeGame, player));
                }    
                    
            }

        } else {
            if (allPicked) {
                ListTurnOrder.turnOrder(event, activeGame);
            }
            if (!msgExtra.isEmpty()) {
                if(!allPicked) {
                    activeGame.updateActivePlayer(privatePlayer);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msgExtra+"\nUse Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeGame, privatePlayer));
                    activeGame.setCurrentPhase("strategy");
                } else {
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msgExtra);
                    MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "\n Use Buttons to do turn.", ButtonHelper.getStartOfTurnButtons(privatePlayer, activeGame, false, event));
                    if(player.getStasisInfantry() > 0){
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), "Use buttons to revive infantry. You have "+player.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeGame, player));
                    }
                }
            }
        }
    }
}
