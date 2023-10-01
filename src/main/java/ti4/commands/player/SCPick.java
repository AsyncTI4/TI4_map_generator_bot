package ti4.commands.player;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
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

    public List<Button> getPlayerOptionsForChecksNBalances(GenericInteractionCreateEvent event, Player player, Game activeGame, int scPicked){
        List<Button> buttons = new ArrayList<Button>();
        List<Player> activePlayers = activeGame.getRealPlayers();
        int maxSCsPerPlayer = activeGame.getSCList().size() / activePlayers.size();
        if(maxSCsPerPlayer < 1){
            maxSCsPerPlayer = 1;
        }
        int minNumOfSCs = 10;
        for(Player p2 : activePlayers){
            if(p2.getSCs().size() < minNumOfSCs){
                minNumOfSCs= p2.getSCs().size();
            }
        }
        if(minNumOfSCs == maxSCsPerPlayer){
            return buttons;
        }
        for(Player p2 : activePlayers){
            if(p2 == player){
                continue;
            }
             if(p2.getSCs().size() == minNumOfSCs){
                if(activeGame.isFoWMode()){
                    buttons.add(Button.secondary("checksNBalancesPt2_"+scPicked+"_"+p2.getFaction(), p2.getColor()));
                }else{
                    buttons.add(Button.secondary("checksNBalancesPt2_"+scPicked+"_"+p2.getFaction(), " ").withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                }
             }
        }
        if(buttons.size() == 0){
            buttons.add(Button.secondary("checksNBalancesPt2_"+scPicked+"_"+player.getFaction(), " ").withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
        }

        return buttons;
    }
    public void secondHalfOfSCPickWhenChecksNBalances(ButtonInteractionEvent event, Player player, Game activeGame, int scPicked) {
        List<Button> buttons = getPlayerOptionsForChecksNBalances(event, player, activeGame, scPicked);
        LinkedHashMap<Integer, Integer> scTradeGoods = activeGame.getScTradeGoods();
       
		for (Player playerStats : activeGame.getRealPlayers()) {
			if (playerStats.getSCs().contains(scPicked)) {
				MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), "SC #"+scPicked+" is already picked.");
				return;
			}
		}
        Integer tgCount = scTradeGoods.get(scPicked);
		if (tgCount != null && tgCount != 0) {
			int tg = player.getTg();
			tg += tgCount;
			MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(),Helper.getPlayerRepresentation(player, activeGame)+" gained "+tgCount +" tgs from picking SC #"+scPicked);
			if (activeGame.isFoWMode()) {
				String messageToSend = Helper.getColourAsMention(event.getGuild(),player.getColor()) +" gained "+tgCount +" tgs from picking SC #"+scPicked;
				FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, messageToSend);
			}
			player.setTg(tg);
			if(player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")){
				ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
			}
			ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
            activeGame.setScTradeGood(scPicked, 0);
		}
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " chose which player to give this SC", buttons);
        event.getMessage().delete().queue();
    }

    public void resolvePt2ChecksNBalances(ButtonInteractionEvent event, Player player, Game activeGame, String buttonID){
        String scPicked = buttonID.split("_")[1];
        int scpick = Integer.parseInt(scPicked);
        String factionPicked = buttonID.split("_")[2];
        Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, factionPicked);
        boolean pickSuccessful = new Stats().secondHalfOfPickSC(event, activeGame, p2, scpick);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " was given SC #"+scpick);
        if(activeGame.isFoWMode()){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), p2.getColor() + " was given SC #"+scpick);

        }
        event.getMessage().delete().queue();
        List<Button> buttons = getPlayerOptionsForChecksNBalances(event, player, activeGame, scpick);
        if(buttons.size() == 0){
            ButtonHelper.startActionPhase(event, activeGame);
        }else{
            boolean foundPlayer = false;
            Player privatePlayer = null;
            for(Player p3: activeGame.getRealPlayers()){
                if(foundPlayer){
                    privatePlayer = p3;
                    foundPlayer = false;
                }
                if(p3 == player){
                    foundPlayer = true;
                }
            }
            if(privatePlayer == null){
                privatePlayer = activeGame.getRealPlayers().get(0);
            }
            activeGame.setCurrentPhase("strategy");
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(privatePlayer, activeGame), ButtonHelper.getTrueIdentity(privatePlayer, activeGame)+"Use Buttons to Pick Which SC you want to give someone", Helper.getRemainingSCButtons(event, activeGame, privatePlayer));
        }
    }

    public void secondHalfOfSCPick(GenericInteractionCreateEvent event, Player player, Game activeGame, int scPicked) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);
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
                if(privatePlayer.getStasisInfantry() > 0){
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(privatePlayer, activeGame), "Use buttons to revive infantry. You have "+privatePlayer.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeGame, privatePlayer));
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
                    if(privatePlayer.getStasisInfantry() > 0){
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(privatePlayer, activeGame), "Use buttons to revive infantry. You have "+privatePlayer.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeGame, privatePlayer));
                    }
                }
            }
        }
        if(allPicked){
            for(Player p2: activeGame.getRealPlayers()){
                List<Button> buttons = new ArrayList<Button>();
                if(p2.hasTechReady("qdn") && p2.getTg() >2 && p2.getStrategicCC() > 0){
                    buttons.add(Button.success("startQDN", "Use Quantum Datahub Node"));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " you have the opportunity to use QDN", buttons);
                }
                if(activeGame.getLaws().containsKey("arbiter") && activeGame.getLawsInfo().get("arbiter").equalsIgnoreCase(p2.getFaction())){
                    buttons.add(Button.success("startArbiter", "Use Imperial Arbiter"));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " you have the opportunity to use QDN", buttons);
                }
            }
        }
    }
}
