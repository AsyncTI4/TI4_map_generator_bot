package ti4.commands.player;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.apache.commons.collections4.ListUtils;

import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.model.PromissoryNoteModel;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.commands.cardspn.PNInfo;

import java.util.*;

public class Turn extends PlayerSubcommandData {
    public Turn() {
        super(Constants.TURN, "End Turn");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player mainPlayer = activeMap.getPlayer(getUser().getId());
        mainPlayer = Helper.getGamePlayer(activeMap, mainPlayer, event, null);
        mainPlayer = Helper.getPlayer(activeMap, mainPlayer, event);

        if (mainPlayer == null) {
            sendMessage("Player/Faction/Color could not be found in map:" + activeMap.getName());
            return;
        }
        if (activeMap.isFoWMode()) {
            MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _");
        } else {
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(),Helper.getPlayerRepresentation(mainPlayer, activeMap)+ " ended turn");
        }
        pingNextPlayer(event, activeMap, mainPlayer);
        //if (!nextMessage.isEmpty()) sendMessage(nextMessage); Sending message in ping next Player
    }

    public void execute(GenericInteractionCreateEvent event, Player mainPlayer, Map activeMap) {
        activeMap.setComponentAction(false);
        if (activeMap.isFoWMode()) {
           MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _");
        } else {
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(),Helper.getPlayerRepresentation(mainPlayer, activeMap) + " ended turn");

        }
        String nextMessage = pingNextPlayer(event, activeMap, mainPlayer);
        if (!nextMessage.isEmpty()) sendMessage(nextMessage);
    }

    public String pingNextPlayer(GenericInteractionCreateEvent event, Map activeMap, Player mainPlayer) {
        activeMap.setComponentAction(false);
        int scNext = -1;
        boolean naaluPresent = false;
        int naaluSC = 0;
        Integer max = Collections.max(activeMap.getScTradeGoods().keySet());

        Boolean privateGame = FoWHelper.isPrivateGame(activeMap, event);
        boolean isFowPrivateGame = privateGame != null && privateGame;

        //MAKE ALL NON-REAL PLAYERS PASSED
        for (Player player : activeMap.getPlayers().values()) {
            if (!player.isRealPlayer()){
                player.setPassed(true);
            }
        }

        //DETERMINE IF NAALU IS PRESENT AND GET THEIR SC
        for (Player player : activeMap.getPlayers().values()) {
            for (int sc : player.getSCs()) {
                String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, activeMap, Integer.toString(sc));
                if (scNumberIfNaaluInPlay.startsWith("0/")) {
                    naaluSC = sc;
                    naaluPresent = true;
                    break;
                }
            }
        }
        if (max == naaluSC) { //quick fix if Naalu picks for e.g. the 8, max is now 7
            max--;
        }

        //FIND CURRENT PLAYER AND if they are holding the highest possible SC, it sets the next SC as 1, otherwise, sets the next SC as current SC+1. 
        for (Player player : activeMap.getPlayers().values()) {
            if (mainPlayer.getUserID().equals(player.getUserID())) {
                int sc = player.getLowestSC();
                scNext = sc;
                String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, activeMap, Integer.toString(sc));
                if (scNumberIfNaaluInPlay.startsWith("0/")) {
                    scNext = 0;
                }
                scNext = scNext == max ? (naaluPresent ? 0 : 1) : scNext + 1;
                break;
            }
        }

        //CREATE LIST OF UNPASSED PLAYERS
        HashMap<Integer, Boolean> scPassed = new HashMap<>();
        for (Player player : activeMap.getPlayers().values()) {
            if (player.isPassed()) {
                continue;
            }
            int sc = player.getLowestSC();
            String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, activeMap, Integer.toString(sc));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                scPassed.put(0, player.isPassed());
            } else {
                scPassed.put(sc, player.isPassed());
            }
        }

        MessageChannel gameChannel = activeMap.getMainGameChannel() == null ? event.getMessageChannel() : activeMap.getMainGameChannel();
        if (scPassed.isEmpty() || scPassed.values().stream().allMatch(value -> value) || activeMap.getPlayers().values().stream().allMatch(Player::isPassed)) {
            
            showPublicObjectivesWhenAllPassed(event, activeMap, gameChannel);
            activeMap.updateActivePlayer(null);
            return "";
        }

        int tempProtection = 0;
        int nextSCFound = -1;
        //Tries to see if the previously determined next up SC is held by an unpassed player. If it is not, it searches the next highest or, if it was at the max, it starts the search over from 0
        while (tempProtection < (activeMap.getPlayers().size() +8)) {
            Boolean isPassed = scPassed.get(scNext);
            if (isPassed != null && !isPassed) {
                nextSCFound = scNext;
                break;
            } else {
                scNext = scNext == max ? (naaluPresent ? 0 : 1) : scNext + 1;
            }
            tempProtection++;
        }

        for (Player player : activeMap.getPlayers().values()) {
            int sc = player.getLowestSC();
            if ((sc != 0 && sc == nextSCFound) || (nextSCFound == 0 && naaluSC == sc)) {
                if(!activeMap.isFoWMode())
                {
                    try {
                        if (activeMap.getLatestTransactionMsg() != null && activeMap.getLatestTransactionMsg() != "") {
                            activeMap.getMainGameChannel().deleteMessageById(activeMap.getLatestTransactionMsg()).queue();
                            activeMap.setLatestTransactionMsg("");
                        }
                    }
                    catch(Exception e) {
                        //  Block of code to handle errors
                    }
                }
                String text = "# " + Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true) + " UP NEXT";
                String buttonText = "Use buttons to do your turn. ";
                List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeMap, false, event);
                activeMap.updateActivePlayer(player);
                activeMap.setCurrentPhase("action");
                if (isFowPrivateGame) {
                    
                    FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, "ended turn");
                    FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, "started turn");
                    
                    String fail = "User for next faction not found. Report to ADMIN";
                    String success = "The next player has been notified";
                    MessageHelper.sendPrivateMessageToPlayer(player, activeMap, event, text, fail, success);
                    MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), buttonText, buttons);
                    if (getMissedSCFollowsText(activeMap, player) != null && !getMissedSCFollowsText(activeMap, player).equalsIgnoreCase("")) {
                        MessageHelper.sendMessageToChannel(player.getPrivateChannel(), getMissedSCFollowsText(activeMap, player));
                    }

                    activeMap.setPingSystemCounter(0);
                    for (int x = 0; x < 10; x++) {
                        activeMap.setTileAsPinged(x, null);
                    }
                    return "";
                } else {
                   MessageHelper.sendMessageToChannel(gameChannel, text);
                    MessageHelper.sendMessageToChannelWithButtons(gameChannel,buttonText, buttons);
                    if (getMissedSCFollowsText(activeMap, player) != null && !getMissedSCFollowsText(activeMap, player).equalsIgnoreCase("")) {
                        MessageHelper.sendMessageToChannel(gameChannel, getMissedSCFollowsText(activeMap, player));
                    }
                    
                    
                    return "";
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Next player not found");
        return "Next Player not found";
    }

    private String getMissedSCFollowsText(Map activeMap, Player player) {
        if (!activeMap.isStratPings()) return null;
        boolean sendReminder = false;

        StringBuilder sb = new StringBuilder("> "+Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+" Please react to ");

        for (int sc : activeMap.getPlayedSCs()) {
            if (!player.hasFollowedSC(sc)) {
                if(!activeMap.isHomeBrewSCMode())
                {
                    sb.append(Helper.getSCBackEmojiFromInteger(sc));
                }
                else
                {
                    sb.append("SC #"+sc);
                }
                
                sendReminder = true;
            }
        }
        sb.append(" above before doing anything else.");
        return sendReminder ? sb.toString() : null;
    }

     public List<Button> getScoreObjectiveButtons(GenericInteractionCreateEvent event, Map activeMap) {
        LinkedHashMap<String, Integer> revealedPublicObjectives = activeMap.getRevealedPublicObjectives();
        HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        LinkedHashMap<String, Integer> customPublicVP = activeMap.getCustomPublicVP();
        List<Button> poButtons = new ArrayList<>();
        List<Button> poButtons1 = new ArrayList<>();
        List<Button> poButtons2 = new ArrayList<>();
        List<Button> poButtonsCustom = new ArrayList<>();
        int poStatus = 0;
        for (java.util.Map.Entry<String, Integer> objective : revealedPublicObjectives.entrySet()) {
            String key = objective.getKey();
            String po_name = publicObjectivesState1.get(key);
            poStatus = 0;
            if (po_name == null) {
                po_name = publicObjectivesState2.get(key);
                poStatus = 1;
            }
            if (po_name == null) {
                Integer integer = customPublicVP.get(key);
                if (integer != null) {
                    if (key.toLowerCase().contains("custodian") || key.toLowerCase().contains("imperial") ||  key.contains("Shard of the Throne")) {
                        //Don't add it for now
                    } else {
                        po_name = key;
                        poStatus = 2;
                    }
                }
            }
            if (po_name != null) {
                Integer value = objective.getValue();
                Button objectiveButton;
                if (poStatus == 0) { //Stage 1 Objectives
                    objectiveButton = Button.success(Constants.PO_SCORING + value, "(" + value + ") " + po_name).withEmoji(Emoji.fromFormatted(Emojis.Public1alt));
                    poButtons1.add(objectiveButton);
                } else if (poStatus == 1) { //Stage 2 Objectives
                    objectiveButton = Button.primary(Constants.PO_SCORING + value, "(" + value + ") " + po_name).withEmoji(Emoji.fromFormatted(Emojis.Public2alt));
                    poButtons2.add(objectiveButton);
                } else if (poStatus == 2) { //Other Objectives
                    objectiveButton = Button.secondary(Constants.PO_SCORING + value, "(" + value + ") " + po_name);
                    poButtonsCustom.add(objectiveButton);
                } else {

                }
            }
        }
       
        poButtons.addAll(poButtons1);
        poButtons.addAll(poButtons2);
        poButtons.addAll(poButtonsCustom);
        poButtons.removeIf(Objects::isNull);
        return poButtons;
     }

    private void showPublicObjectivesWhenAllPassed(GenericInteractionCreateEvent event, Map activeMap, MessageChannel gameChannel) {
        String message = "All players passed. Please score objectives. " + Helper.getGamePing(event, activeMap);
        activeMap.setCurrentPhase("status");
        List<Button> poButtons = getScoreObjectiveButtons(event, activeMap);
        Button noPOScoring = Button.danger(Constants.PO_NO_SCORING, "No PO Scored");
        Button noSOScoring = Button.danger(Constants.SO_NO_SCORING, "No SO Scored");
        poButtons.add(noPOScoring);
        poButtons.add(noSOScoring);
        poButtons.removeIf(Objects::isNull);
        List<List<Button>> partitions = ListUtils.partition(poButtons, 5);
        List<ActionRow> actionRows = new ArrayList<>();
        for (List<Button> partition : partitions) {
            actionRows.add(ActionRow.of(partition));
        }
        MessageCreateData messageObject = new MessageCreateBuilder()
                .addContent(message)
                .addComponents(actionRows).build();

        gameChannel.sendMessage(messageObject).queue();
        
        // return beginning of status phase PNs
        LinkedHashMap<String, Player> players = activeMap.getPlayers();
         for (Player player : players.values()) {
           List<String> pns = new ArrayList<String>();
            pns.addAll(player.getPromissoryNotesInPlayArea());
            for(String pn: pns){
                Player pnOwner = activeMap.getPNOwner(pn);
                if(!pnOwner.isRealPlayer()){
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if(pnModel.getText().contains("eturn this card") && (pnModel.getText().contains("start of the status phase") || pnModel.getText().contains("beginning of the status phase"))){
                        player.removePromissoryNote(pn);
                        pnOwner.setPromissoryNote(pn);  
                        PNInfo.sendPromissoryNoteInfo(activeMap, pnOwner, false);
		                PNInfo.sendPromissoryNoteInfo(activeMap, player, false);
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), pnModel.getName() + " was returned");
                    }
                }
            }
    
        for(Player p2 : activeMap.getRealPlayers()){
            String ms2 = getMissedSCFollowsText(activeMap, p2);
            if (ms2 != null && !ms2.equalsIgnoreCase("")) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeMap), ms2);
            }
        }

        Player arborec = Helper.getPlayerFromAbility(activeMap, "mitosis");
        if (arborec != null) {
            String mitosisMessage = Helper.getPlayerRepresentation(arborec, activeMap, event.getGuild(), true) + " reminder to do mitosis!";
            MessageHelper.sendMessageToChannel((MessageChannel) arborec.getCardsInfoThread(activeMap), mitosisMessage);
            
        }
        Player solPlayer =  Helper.getPlayerFromColorOrFaction(activeMap, "sol");

        if (solPlayer != null) {
            String colorID = Mapper.getColorID(solPlayer.getColor());
            String fsKey = colorID + "_fs.png";
            String infKey = colorID + "_gf.png";
            for (Tile tile : activeMap.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder.getUnits() != null) {
                        if (unitHolder.getUnits().get(fsKey) != null && unitHolder.getUnits().get(fsKey) > 0) {
                            unitHolder.addUnit(infKey, 1);
                            String genesisMessage = Helper.getPlayerRepresentation(solPlayer, activeMap, event.getGuild(), true) + " an infantry was added to the space area of your flagship automatically.";
                            if (activeMap.isFoWMode()) {
                                MessageHelper.sendMessageToChannel(solPlayer.getPrivateChannel(), genesisMessage);
                            } else {
                                MessageHelper.sendMessageToChannel(gameChannel, genesisMessage);
                            }
                        }
                    }
                }
            }
        }
    }
}
