package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
        HashMap<Integer, String> order = new HashMap<>();
        for (Player player : map.getPlayers().values()) {
            int sc = player.getSC();
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
            order.put(sc, text);
        }
        Integer max = Collections.max(map.getScTradeGoods().keySet());
        StringBuilder msg = new StringBuilder();
        for (int i = 1; i <= max; i++) {
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
