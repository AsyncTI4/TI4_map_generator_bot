package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collections;
import java.util.HashMap;

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
            } else {
                scPassed.put(sc, player.isPassed());
            }
        }
        if (scPassed.isEmpty() || scPassed.values().stream().allMatch(value -> value)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "All players passed. Please score objectives or react with your faction symbol for no scoring. " + Helper.getGamePing(event, map));
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
        for (Player player : map.getPlayers().values()) {
            int sc = player.getSC();
            if (sc != 0 && sc == nextSCFound || nextSCFound == 0 && naaluSC == sc) {
                String text = "";
                text += Helper.getFactionIconFromDiscord(player.getFaction());
                text += " " + Helper.getPlayerPing(player) + " UP NEXT";
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
