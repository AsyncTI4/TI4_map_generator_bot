package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collections;
import java.util.HashMap;

public class ListTurnOrder extends StatusSubcommandData {
    public ListTurnOrder() {
        super(Constants.TURN_ORDER, "List Turn order with SC played and Player passed status");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map map = getActiveMap();
        turnOrder(event, map);
    }

    public static void turnOrder(SlashCommandInteractionEvent event, Map map) {
        HashMap<Integer, String> order = new HashMap<>();
        int naaluSC = 0;
        for (Player player : map.getPlayers().values()) {
            int sc = player.getSC();
            String scNumberIfNaaluInPlay = GenerateMap.getSCNumberIfNaaluInPlay(player, map, Integer.toString(sc));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                naaluSC = sc;
            }
            boolean passed = player.isPassed();
            String userName = player.getUserName();
            String color = player.getColor();
            HashMap<Integer, Boolean> scPlayed = map.getScPlayed();
            Boolean found = scPlayed.get(sc);
            boolean isPlayed = found != null ? found : false;
            String text = "";
            if (isPlayed) {
                text += "~~";
            }
            text += Helper.getSCAsMention(sc);
            if (isPlayed) {
                text += "~~";
            }
            if (passed) {
                text += "~~";
            }
            text += Helper.getFactionIconFromDiscord(player.getFaction());
            text += " " + userName;
            if (color != null) {
                text += " (" + color + ")";
            }
            if (passed) {
                text += "~~ - PASSED";
            }
            
            if(player.getUserName().equals(map.getSpeaker())) {
            	text += " :Speakertoken: ";
            }
            
            order.put(sc, text);
        }
        StringBuilder msg = new StringBuilder();


        if (naaluSC != 0) {
            String text = order.get(naaluSC);
            msg.append(0).append(". ").append(text).append("\n");
        }
        Integer max = Collections.max(map.getScTradeGoods().keySet());
        for (int i = 1; i <= max; i++) {
            if (naaluSC != 0 && i == naaluSC) {
                continue;
            }
            String text = order.get(i);
            if (text != null) {
                msg.append(i).append(". ").append(text).append("\n");
            }
        }
        MessageHelper.replyToMessage(event, msg.toString());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        //We reply in execute command
    }
}
