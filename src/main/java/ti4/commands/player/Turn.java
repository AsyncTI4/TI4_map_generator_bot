package ti4.commands.player;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.apache.commons.collections4.ListUtils;

import ti4.MapGenerator;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

import java.util.*;

public class Turn extends PlayerSubcommandData {
    public Turn() {
        super(Constants.TURN, "End Turn");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map map = getActiveMap();
        Player mainPlayer = map.getPlayer(getUser().getId());
        mainPlayer = Helper.getGamePlayer(map, mainPlayer, event, null);
        mainPlayer = Helper.getPlayer(map, mainPlayer, event);
        
        if (mainPlayer == null) {
            sendMessage("Player/Faction/Color could not be found in map:" + map.getName());
            return;
        }
        if(map.isFoWMode()) {
            MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _");
        } else {
            MessageHelper.sendMessageToChannel(map.getMainGameChannel(),Helper.getPlayerRepresentation(event, mainPlayer));
        }
        String nextMessage = pingNextPlayer(event, map, mainPlayer);
        if (!nextMessage.isEmpty()) sendMessage(nextMessage);
    }
    public void execute(GenericInteractionCreateEvent event, Player mainPlayer, Map map) {

        if(map.isFoWMode()) {
           MessageHelper.sendMessageToChannel(mainPlayer.getPrivateChannel(), "_ _");
        } else {
            MessageHelper.sendMessageToChannel(map.getMainGameChannel(),Helper.getPlayerRepresentation(event, mainPlayer));
            
        }
        String nextMessage = pingNextPlayer(event, map, mainPlayer);
        if (!nextMessage.isEmpty()) sendMessage(nextMessage);
    }



    public String pingNextPlayer(GenericInteractionCreateEvent event, Map map, Player mainPlayer) {
        int scNext = -1;
        boolean naaluPresent = false;
        int naaluSC = 0;
        Integer max = Collections.max(map.getScTradeGoods().keySet());

        Boolean privateGame = FoWHelper.isPrivateGame(map, event);
        boolean isFowPrivateGame = privateGame != null && privateGame;

        //MAKE ALL NON-REAL PLAYERS PASSED
        for (Player player : map.getPlayers().values()) {
            if (!player.isRealPlayer()){
                player.setPassed(true);
            }
        }

        //DETERMINE IF NAALU IS PRESENT AND GET THEIR SC
        for (Player player : map.getPlayers().values()) {
            for (int sc : player.getSCs()) {
                String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, map, Integer.toString(sc));
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

        //FIND CURRENT PLAYER AND ???
        for (Player player : map.getPlayers().values()) {
            if (mainPlayer.getUserID().equals(player.getUserID())) {
                int sc = player.getLowestSC();
                scNext = sc;
                String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, map, Integer.toString(sc));
                if (scNumberIfNaaluInPlay.startsWith("0/")) {
                    scNext = 0;
                }
                scNext = scNext == max ? (naaluPresent ? 0 : 1) : scNext + 1;
                break;
            }
        }

        //CREATE LIST OF UNPASSED PLAYERS
        HashMap<Integer, Boolean> scPassed = new HashMap<>();
        for (Player player : map.getPlayers().values()) {
            if (player.isPassed()) {
                continue;
            }
            int sc = player.getLowestSC();
            String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, map, Integer.toString(sc));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                scPassed.put(0, player.isPassed());
                if (player.isPassed()) {
                    scPassed.put(sc, player.isPassed());
                }
            } else {
                scPassed.put(sc, player.isPassed());
            }
        }

        MessageChannel gameChannel = map.getMainGameChannel() == null ? event.getMessageChannel() : map.getMainGameChannel();
        if (scPassed.isEmpty() || scPassed.values().stream().allMatch(value -> value) || map.getPlayers().values().stream().allMatch(Player::isPassed)) {
            map.updateActivePlayer(null);
            showPublicObjectivesWhenAllPassed(event, map, gameChannel);
            return "";
        }

        int tempProtection = 0;
        int nextSCFound = -1;
        while (tempProtection < (map.getSCList().size() + 5)) {
            Boolean isPassed = scPassed.get(scNext);
            if (isPassed != null && !isPassed) {
                nextSCFound = scNext;
                break;
            } else {
                scNext = scNext == max ? (naaluPresent ? 0 : 1) : scNext + 1;
            }
            tempProtection++;
        }

        for (Player player : map.getPlayers().values()) {
            int sc = player.getLowestSC();
            if (sc != 0 && sc == nextSCFound || nextSCFound == 0 && naaluSC == sc) {
                String text = Helper.getPlayerRepresentation(event, player, true) + " UP NEXT";
                map.updateActivePlayer(player);
                if (isFowPrivateGame) {
                    String fail = "User for next faction not found. Report to ADMIN";
                    String success = "The next player has been notified";
                    MessageHelper.sendPrivateMessageToPlayer(player, map, event, text, fail, success);
                    if(getMissedSCFollowsText(map, player) != null && !getMissedSCFollowsText(map, player).equalsIgnoreCase(""))
                    {
                        MessageHelper.sendMessageToChannel(player.getPrivateChannel(), getMissedSCFollowsText(map, player));
                    }
                  
                    map.setPingSystemCounter(0);
                    for(int x = 0; x < 10; x++)
                    {
                        map.setTileAsPinged(x, null);
                    }
                    return "";
                } else {
                    MessageHelper.sendMessageToChannel(gameChannel, text);
                    if(getMissedSCFollowsText(map, player) != null && !getMissedSCFollowsText(map, player).equalsIgnoreCase(""))
                    {
                        MessageHelper.sendMessageToChannel(gameChannel, getMissedSCFollowsText(map, player));
                    }
                    return "";
                }
            }
        }
        return "Next Player not found";
    }

    private String getMissedSCFollowsText(Map map, Player player) {
        if (!map.isStratPings()) return null;
        boolean sendReminder = false;
        
        StringBuilder sb = new StringBuilder("> Please react to ");

        for (int sc : map.getPlayedSCs()) {
            if (!player.hasFollowedSC(sc)) {   
                sb.append(Helper.getSCBackEmojiFromInteger(sc));
                sendReminder = true;
            }
        }
        sb.append(" above before taking your turn.");
        return sendReminder ? sb.toString() : null;
    }

    private void showPublicObjectivesWhenAllPassed(GenericInteractionCreateEvent event, Map map, MessageChannel gameChannel) {
        String message = "All players passed. Please score objectives. " + Helper.getGamePing(event, map);
        
        LinkedHashMap<String, Integer> revealedPublicObjectives = map.getRevealedPublicObjectives();
        Player arborec = Helper.getPlayerFromColorOrFaction(map, "arborec");
        if(arborec != null)
        {
            String mitosisMessage = Helper.getPlayerRepresentation(event, arborec, true) + " reminder to do mitosis!";
            if(map.isFoWMode())
            {
                MessageHelper.sendMessageToChannel(arborec.getPrivateChannel(), mitosisMessage);
            }
            else
            {
                MessageHelper.sendMessageToChannel(gameChannel, mitosisMessage);
            }
        }
        Player Sol =  Helper.getPlayerFromColorOrFaction(map, "sol");
    
        if(Sol != null)
        {
            String colorID = Mapper.getColorID(Sol.getColor());
            String fsKey = colorID + "_fs.png";
            String infKey = colorID + "_gf.png";
            for (Tile tile : map.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder.getUnits() != null)
                    {
                        if(unitHolder.getUnits().get(fsKey) != null && unitHolder.getUnits().get(fsKey) > 0)
                        {
                            unitHolder.addUnit(infKey, 1);
                            String genesisMessage = Helper.getPlayerRepresentation(event, Sol, true) + " an infantry was added to the space area of your flagship automatically.";
                            if(map.isFoWMode())
                            {
                                MessageHelper.sendMessageToChannel(Sol.getPrivateChannel(), genesisMessage);
                            }
                            else
                            {
                                MessageHelper.sendMessageToChannel(gameChannel, genesisMessage);
                            }
                        }
                    }
                }
            }
        }

        Player L1 =  Helper.getPlayerFromColorOrFaction(map, "l1z1x");

        if(L1 != null)
        {
            for(Player player : map.getPlayers().values())
            {
 
                if (!player.getPromissoryNotes().isEmpty()) {
                    for (String pn : player.getPromissoryNotes().keySet()) {
                        
                        if (!player.getFaction().equalsIgnoreCase("l1z1x") && pn.equalsIgnoreCase("ce")) {
                            String cyberMessage = Helper.getPlayerRepresentation(event, player, true) + " reminder to use cybernetic enhancements!";
                            if(map.isFoWMode())
                            {
                                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), cyberMessage);
                            }
                            else
                            {
                                MessageHelper.sendMessageToChannel(gameChannel, cyberMessage);
                            }
                        }
                            
                        
                    }
                }
            }
        }

        HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesState1();
        HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesState2();
        LinkedHashMap<String, Integer> customPublicVP = map.getCustomPublicVP();
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

        Button noPOScoring = Button.danger(Constants.PO_NO_SCORING, "No PO Scored");
        Button noSOScoring = Button.danger(Constants.SO_NO_SCORING, "No SO Scored");
        poButtons.addAll(poButtons1);
        poButtons.addAll(poButtons2);
        poButtons.addAll(poButtonsCustom);
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
    }
}
