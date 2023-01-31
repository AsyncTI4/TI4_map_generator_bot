package ti4.commands.player;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
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
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player/Faction/Color could not be found in map:" + map.getName());
            return;
        }
        pingNextPlayer(event, map, mainPlayer);
    }

    public static void pingNextPlayer(SlashCommandInteractionEvent event, Map map, Player mainPlayer) {
        int scNext = -1;
        boolean naaluPresent = false;
        int naaluSC = 0;
        Integer max = Collections.max(map.getScTradeGoods().keySet());

        for (Player player : map.getPlayers().values()) {
            if (player.getFaction() == null || player.getColor() == null || player.getColor().equals("white")){
                player.setPassed(true);
            }
        }
        for (Player player : map.getPlayers().values()) {
            String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, map, Integer.toString(player.getSC()));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                naaluSC = player.getSC();
                naaluPresent = true;
                break;
            }
        }
        if (max == naaluSC) {
            max--;
        }
        for (Player player : map.getPlayers().values()) {
            if (mainPlayer.getUserID().equals(player.getUserID())) {
                int sc = player.getSC();
                scNext = sc;
                String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, map, Integer.toString(sc));
                if (scNumberIfNaaluInPlay.startsWith("0/")) {
                    scNext = 0;
                }
                scNext = scNext == max ? (naaluPresent ? 0 : 1) : scNext + 1;
                break;
            }
        }
        HashMap<Integer, Boolean> scPassed = new HashMap<>();
        for (Player player : map.getPlayers().values()) {
            if (player.isPassed()) {
                continue;
            }
            int sc = player.getSC();
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
        if (scPassed.isEmpty() || scPassed.values().stream().allMatch(value -> value) || map.getPlayers().values().stream().allMatch(Player::isPassed)) {
            String message = "All players passed. Please score objectives. " + Helper.getGamePing(event, map);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);

            LinkedHashMap<String, Integer> revealedPublicObjectives = map.getRevealedPublicObjectives();

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
                        if (key.toLowerCase().contains("custodian") || key.toLowerCase().contains("imperial")) {
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
                        objectiveButton = Button.success(Constants.PO_SCORING + value, "(" + value + ") " + po_name).withEmoji(Emoji.fromMarkdown(Emojis.Public1alt));
                        poButtons1.add(objectiveButton);
                    } else if (poStatus == 1) { //Stage 2 Objectives
                        objectiveButton = Button.primary(Constants.PO_SCORING + value, "(" + value + ") " + po_name).withEmoji(Emoji.fromMarkdown(Emojis.Public2alt));
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
            Message messageObject = new MessageBuilder()
                    .append(message)
                    .setActionRows(actionRows).build();
            MessageChannel channel = event.getChannel();
            channel.sendMessage(messageObject).queue();


            MessageHelper.replyToMessageTI4Logo(event);
            return;
        }

        int tempProtection = 0;
        int nextSCFound = -1;
        while (tempProtection < 20) {
            Boolean isPassed = scPassed.get(scNext);
            if (isPassed != null && !isPassed) {
                nextSCFound = scNext;
                break;
            } else {
                scNext = scNext == max ? (naaluPresent ? 0 : 1) : scNext + 1;
            }
            tempProtection++;
        }
        Boolean privateGame = FoWHelper.isPrivateGame(map, event);
        for (Player player : map.getPlayers().values()) {
            int sc = player.getSC();
            if (sc != 0 && sc == nextSCFound || nextSCFound == 0 && naaluSC == sc) {
                String text = "";
                text += Helper.getPlayerRepresentation(event, player) + " UP NEXT";
                if (privateGame != null && privateGame){
                    User user = MapGenerator.jda.getUserById(player.getUserID());
                    if (user == null) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "User for faction not found. Report to ADMIN");
                    } else {
                        MessageHelper.sentToMessageToUser(event, map.getName() + " " + text, user);
                    }
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), text);
                return;
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Next Player not found");
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
